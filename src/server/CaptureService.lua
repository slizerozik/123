-- Капты: зоны-парты в Workspace.CaptureZones, которые кланы захватывают, стоя внутри.
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Workspace = game:GetService("Workspace")

local Config = require(ReplicatedStorage:WaitForChild("ClanShared"):WaitForChild("Config"))

local CaptureService = {}

local ClanService
local capturedRemote
local zones = {} -- BasePart -> зона

-- Зона: holder — клан, которому принадлежит шкала control (0..1);
-- ownerId — клан, полностью захвативший зону (либо nil, либо равен holder.id).

local function createBillboard(part)
	local gui = Instance.new("BillboardGui")
	gui.Name = "ZoneInfo"
	gui.Size = UDim2.fromOffset(220, 56)
	gui.StudsOffsetWorldSpace = Vector3.new(0, part.Size.Y / 2 + 10, 0)
	gui.AlwaysOnTop = true
	gui.MaxDistance = 400

	local title = Instance.new("TextLabel")
	title.Size = UDim2.new(1, 0, 0, 34)
	title.BackgroundTransparency = 1
	title.Font = Enum.Font.GothamBold
	title.TextScaled = true
	title.TextColor3 = Color3.new(1, 1, 1)
	title.TextStrokeTransparency = 0.3
	title.Parent = gui

	local bar = Instance.new("Frame")
	bar.Position = UDim2.new(0, 0, 0, 40)
	bar.Size = UDim2.new(1, 0, 0, 12)
	bar.BackgroundColor3 = Color3.fromRGB(30, 30, 30)
	bar.BorderSizePixel = 0
	bar.Parent = gui

	local fill = Instance.new("Frame")
	fill.Size = UDim2.fromScale(0, 1)
	fill.BorderSizePixel = 0
	fill.Parent = bar

	gui.Parent = part
	return title, fill
end

local function render(zone)
	local part = zone.part
	local holder = zone.holder
	local color = holder and holder.color or Config.NeutralColor

	part:SetAttribute("OwnerTag", zone.ownerId and holder.tag or nil)
	part:SetAttribute("HolderTag", holder and holder.tag or nil)
	part:SetAttribute("HolderColor", color)
	part:SetAttribute("Control", zone.control)
	part:SetAttribute("Contested", zone.contested)

	if zone.contested then
		zone.title.Text = `{zone.name} — бой!`
	elseif zone.ownerId then
		zone.title.Text = `{zone.name} [{holder.tag}]`
	elseif holder then
		zone.title.Text = `{zone.name} — захват [{holder.tag}]`
	else
		zone.title.Text = `{zone.name} — нейтральная`
	end
	zone.fill.Size = UDim2.fromScale(zone.control, 1)
	zone.fill.BackgroundColor3 = color
	if Config.RecolorZones then
		part.Color = Config.NeutralColor:Lerp(color, zone.control)
	end
end

local function addZone(part)
	if not part:IsA("BasePart") or zones[part] then
		return
	end
	local title, fill = createBillboard(part)
	local zone = {
		part = part,
		name = part:GetAttribute("DisplayName") or part.Name,
		ownerId = nil,
		holder = nil,
		control = 0,
		contested = false,
		rewardTimer = 0,
		title = title,
		fill = fill,
	}
	part:SetAttribute("ZoneName", zone.name)
	zones[part] = zone
	render(zone)
end

local function isInside(part, position)
	local point = part.CFrame:PointToObjectSpace(position)
	local half = part.Size / 2
	return math.abs(point.X) <= half.X
		and math.abs(point.Z) <= half.Z
		and point.Y >= -half.Y - 3
		and point.Y <= half.Y + Config.ZoneHeight
end

local function alivePositions()
	local positions = {}
	for _, player in Players:GetPlayers() do
		local character = player.Character
		local humanoid = character and character:FindFirstChildOfClass("Humanoid")
		local root = character and character:FindFirstChild("HumanoidRootPart")
		if humanoid and root and humanoid.Health > 0 then
			positions[player] = root.Position
		end
	end
	return positions
end

local function stepZone(zone, clanCounts, clanData, dt)
	local presentId, presentCount, distinct = nil, 0, 0
	for clanId, count in clanCounts do
		distinct += 1
		presentId, presentCount = clanId, count
	end

	zone.contested = distinct > 1
	local holder = zone.holder

	if distinct == 1 then
		local multiplier = math.min(Config.MaxSpeedMultiplier, 1 + (presentCount - 1) * Config.ExtraPlayerBonus)
		local amount = dt / Config.CaptureTime * multiplier

		if holder and holder.id ~= presentId then
			-- Сначала сбиваем чужой захват до нуля.
			zone.control -= amount
			if zone.control <= 0 then
				zone.control = 0
				zone.ownerId = nil
				zone.holder = nil
			end
		else
			if not holder then
				local clan = clanData[presentId]
				zone.holder = {
					id = clan.id,
					name = clan.name,
					tag = clan.tag,
					color = Config.Colors[clan.color] or Config.Colors[1],
				}
			end
			zone.control = math.min(1, zone.control + amount)
			if zone.control >= 1 and zone.ownerId ~= presentId then
				zone.ownerId = presentId
				zone.rewardTimer = 0
				ClanService.AddPoints(presentId, Config.CaptureReward)
				capturedRemote:FireAllClients(zone.name, zone.holder.name, zone.holder.tag)
			end
		end
	elseif distinct == 0 and holder then
		-- Пустая зона: у владельца шкала восстанавливается, незавершённый захват откатывается.
		local amount = dt / Config.DecayTime
		if zone.ownerId then
			zone.control = math.min(1, zone.control + amount)
		else
			zone.control -= amount
			if zone.control <= 0 then
				zone.control = 0
				zone.holder = nil
			end
		end
	end

	if zone.ownerId then
		zone.rewardTimer += dt
		if zone.rewardTimer >= Config.HoldRewardInterval then
			zone.rewardTimer -= Config.HoldRewardInterval
			ClanService.AddPoints(zone.ownerId, Config.HoldReward)
		end
	end
end

local function step(dt)
	local positions = alivePositions()
	local playerZone = {}

	for part, zone in zones do
		local clanCounts, clanData = {}, {}
		for player, position in positions do
			if not playerZone[player] and isInside(part, position) then
				playerZone[player] = part.Name
				local clan = ClanService.GetPlayerClan(player)
				if clan then
					clanCounts[clan.id] = (clanCounts[clan.id] or 0) + 1
					clanData[clan.id] = clan
				end
			end
		end
		stepZone(zone, clanCounts, clanData, dt)
		render(zone)
	end

	for _, player in Players:GetPlayers() do
		player:SetAttribute("CurrentZone", playerZone[player])
	end
end

function CaptureService.Init(clanService)
	ClanService = clanService

	capturedRemote = Instance.new("RemoteEvent")
	capturedRemote.Name = "ZoneCaptured"
	capturedRemote.Parent = ClanService.Remotes

	local folder = Workspace:FindFirstChild(Config.ZonesFolderName)
	if not folder then
		folder = Instance.new("Folder")
		folder.Name = Config.ZonesFolderName
		folder.Parent = Workspace
		warn(`[Captures] В Workspace нет папки {Config.ZonesFolderName}, создана пустая. Добавьте в неё парты-зоны.`)
	end
	for _, child in folder:GetChildren() do
		addZone(child)
	end
	folder.ChildAdded:Connect(addZone)
	folder.ChildRemoved:Connect(function(child)
		zones[child] = nil
	end)

	-- Распущенный клан теряет все зоны.
	ClanService.ClanRemoved:Connect(function(clanId)
		for _, zone in zones do
			if zone.holder and zone.holder.id == clanId then
				zone.holder = nil
				zone.ownerId = nil
				zone.control = 0
				render(zone)
			end
		end
	end)

	task.spawn(function()
		while true do
			step(task.wait(Config.TickInterval))
		end
	end)
end

return CaptureService
