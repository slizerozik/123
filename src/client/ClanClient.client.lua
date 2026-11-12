-- Интерфейс кланов и индикатор захвата зоны. Весь GUI создаётся кодом.
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")
local Workspace = game:GetService("Workspace")

local Config = require(ReplicatedStorage:WaitForChild("ClanShared"):WaitForChild("Config"))
local Ranks = Config.Ranks

local remotes = ReplicatedStorage:WaitForChild("ClanRemotes")
local request = remotes:WaitForChild("Request")
local stateChanged = remotes:WaitForChild("StateChanged")
local zoneCaptured = remotes:WaitForChild("ZoneCaptured")

local player = Players.LocalPlayer

local BACKGROUND = Color3.fromRGB(28, 30, 36)
local PANEL = Color3.fromRGB(44, 47, 56)
local ACCENT = Color3.fromRGB(52, 152, 219)
local DANGER = Color3.fromRGB(192, 57, 43)
local GOOD = Color3.fromRGB(39, 174, 96)
local TEXT = Color3.fromRGB(240, 240, 240)

local state = { clan = nil, invites = {} }
local draft = { name = "", tag = "", invite = "", color = 1 }

---------------------------------------------------------------------
-- Помощники для GUI
---------------------------------------------------------------------

local function make(className, props, parent)
	local instance = Instance.new(className)
	for key, value in props do
		instance[key] = value
	end
	instance.Parent = parent
	return instance
end

local function round(instance)
	make("UICorner", { CornerRadius = UDim.new(0, 6) }, instance)
end

local function label(text, parent, props)
	local instance = make("TextLabel", {
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		Font = Enum.Font.Gotham,
		TextSize = 15,
		TextColor3 = TEXT,
		TextXAlignment = Enum.TextXAlignment.Left,
		TextTruncate = Enum.TextTruncate.AtEnd,
		Text = text,
	}, parent)
	for key, value in props or {} do
		instance[key] = value
	end
	return instance
end

local function button(text, color, parent, props, onClick)
	local instance = make("TextButton", {
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = color,
		AutoButtonColor = true,
		Font = Enum.Font.GothamMedium,
		TextSize = 14,
		TextColor3 = TEXT,
		Text = text,
	}, parent)
	for key, value in props or {} do
		instance[key] = value
	end
	round(instance)
	instance.Activated:Connect(function()
		onClick(instance)
	end)
	return instance
end

-- Опасные действия требуют второго нажатия.
local function confirmButton(text, color, parent, props, onConfirm)
	local armedAt = 0
	return button(text, color, parent, props, function(instance)
		if os.clock() - armedAt < 3 then
			onConfirm()
			return
		end
		armedAt = os.clock()
		instance.Text = "Точно?"
		task.delay(3, function()
			if instance.Parent then
				instance.Text = text
			end
		end)
	end)
end

local function textBox(placeholder, text, parent, props, onChanged)
	local instance = make("TextBox", {
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = PANEL,
		Font = Enum.Font.Gotham,
		TextSize = 15,
		TextColor3 = TEXT,
		PlaceholderText = placeholder,
		PlaceholderColor3 = Color3.fromRGB(140, 140, 150),
		ClearTextOnFocus = false,
		Text = text,
	}, parent)
	for key, value in props or {} do
		instance[key] = value
	end
	round(instance)
	instance:GetPropertyChangedSignal("Text"):Connect(function()
		onChanged(instance.Text)
	end)
	return instance
end

---------------------------------------------------------------------
-- Каркас окна
---------------------------------------------------------------------

local gui = make("ScreenGui", {
	Name = "ClanGui",
	ResetOnSpawn = false,
	ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
}, player:WaitForChild("PlayerGui"))

local window = make("Frame", {
	Size = UDim2.fromOffset(420, 470),
	AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.5),
	BackgroundColor3 = BACKGROUND,
	BorderSizePixel = 0,
	Visible = false,
}, gui)
round(window)

label("Кланы", window, {
	Size = UDim2.new(1, -24, 0, 40),
	Position = UDim2.fromOffset(12, 0),
	Font = Enum.Font.GothamBold,
	TextSize = 18,
})

local content = make("Frame", {
	Size = UDim2.new(1, -24, 1, -84),
	Position = UDim2.fromOffset(12, 42),
	BackgroundTransparency = 1,
}, window)

local status = label("", window, {
	Size = UDim2.new(1, -24, 0, 30),
	Position = UDim2.new(0, 12, 1, -36),
	TextSize = 14,
	TextTruncate = Enum.TextTruncate.None,
	TextWrapped = true,
})

local toggle = button("Кланы", ACCENT, gui, {
	Size = UDim2.fromOffset(90, 34),
	AnchorPoint = Vector2.new(0, 0.5),
	Position = UDim2.new(0, 12, 0.5, 0),
}, function()
	window.Visible = not window.Visible
end)
toggle.Name = "Toggle"

button("X", DANGER, window, {
	Size = UDim2.fromOffset(28, 28),
	Position = UDim2.new(1, -36, 0, 6),
}, function()
	window.Visible = false
end)

local function setStatus(text, success)
	status.Text = text
	status.TextColor3 = success and GOOD or Color3.fromRGB(231, 120, 100)
end

local function call(action, ...)
	local ok, success, message = pcall(request.InvokeServer, request, action, ...)
	if not ok then
		setStatus("Сервер не отвечает", false)
		return false
	end
	if message then
		setStatus(message, success)
	end
	return success
end

---------------------------------------------------------------------
-- Содержимое окна
---------------------------------------------------------------------

local rowOrder = 0

local function row(height)
	rowOrder += 1
	return make("Frame", {
		Size = UDim2.new(1, 0, 0, height),
		BackgroundTransparency = 1,
		LayoutOrder = rowOrder,
	}, content)
end

local function buildNoClan()
	label("Создать клан", row(24), { Font = Enum.Font.GothamBold })

	textBox("Название клана", draft.name, row(34), nil, function(text)
		draft.name = text
	end)

	local tagRow = row(34)
	textBox("Тег", draft.tag, tagRow, { Size = UDim2.new(0.5, -4, 1, 0) }, function(text)
		draft.tag = text
	end)
	button("Цвет клана", Config.Colors[draft.color], tagRow, {
		Size = UDim2.new(0.5, -4, 1, 0),
		Position = UDim2.new(0.5, 4, 0, 0),
	}, function(instance)
		draft.color = draft.color % #Config.Colors + 1
		instance.BackgroundColor3 = Config.Colors[draft.color]
	end)

	button("Создать", GOOD, row(36), nil, function()
		if call("Create", draft.name, draft.tag, draft.color) then
			draft.name, draft.tag = "", ""
		end
	end)

	label("Приглашения", row(28), { Font = Enum.Font.GothamBold })
	if #state.invites == 0 then
		label("Пока никто не пригласил вас в клан", row(24), { TextColor3 = Color3.fromRGB(150, 150, 160) })
	end
	for _, invite in state.invites do
		local inviteRow = row(34)
		label(`[{invite.tag}] {invite.name} — от {invite.from}`, inviteRow, { Size = UDim2.new(1, -170, 1, 0) })
		button("Принять", GOOD, inviteRow, {
			Size = UDim2.fromOffset(80, 28),
			Position = UDim2.new(1, -164, 0, 3),
		}, function()
			call("AcceptInvite", invite.clanId)
		end)
		button("Нет", DANGER, inviteRow, {
			Size = UDim2.fromOffset(76, 28),
			Position = UDim2.new(1, -78, 0, 3),
		}, function()
			call("DeclineInvite", invite.clanId)
		end)
	end
end

local function buildMemberRow(list, member, myRank)
	local memberRow = make("Frame", {
		Size = UDim2.new(1, -8, 0, 32),
		BackgroundColor3 = PANEL,
		BorderSizePixel = 0,
	}, list)
	round(memberRow)

	local buttons = {}
	if member.userId ~= player.UserId then
		if myRank == Ranks.Owner then
			local promote = member.rank < Ranks.Officer
			table.insert(buttons, {
				text = promote and "Повысить" or "Понизить",
				color = ACCENT,
				run = function()
					call("SetRank", member.userId, promote and Ranks.Officer or Ranks.Member)
				end,
			})
			table.insert(buttons, {
				text = "Лидер",
				color = GOOD,
				confirm = true,
				run = function()
					call("Transfer", member.userId)
				end,
			})
		end
		if myRank >= Ranks.Officer and myRank > member.rank then
			table.insert(buttons, {
				text = "Кик",
				color = DANGER,
				confirm = true,
				run = function()
					call("Kick", member.userId)
				end,
			})
		end
	end

	local width = 72
	for index, info in buttons do
		local props = {
			Size = UDim2.fromOffset(width - 4, 24),
			Position = UDim2.new(1, -(#buttons - index + 1) * width, 0, 4),
			TextSize = 12,
		}
		if info.confirm then
			confirmButton(info.text, info.color, memberRow, props, info.run)
		else
			button(info.text, info.color, memberRow, props, info.run)
		end
	end

	label(`{member.name} · {Config.RankNames[member.rank] or "?"}`, memberRow, {
		Size = UDim2.new(1, -(#buttons * width + 16), 1, 0),
		Position = UDim2.fromOffset(8, 0),
		TextColor3 = member.online and TEXT or Color3.fromRGB(130, 130, 140),
	})
end

local function buildClan(clan)
	local myRank = 0
	for _, member in clan.members do
		if member.userId == player.UserId then
			myRank = member.rank
		end
	end

	local header = row(30)
	label(`[{clan.tag}] {clan.name}`, header, {
		Size = UDim2.new(0.6, 0, 1, 0),
		Font = Enum.Font.GothamBold,
		TextSize = 17,
		TextColor3 = Config.Colors[clan.color] or TEXT,
	})
	label(`Очки: {clan.points}`, header, {
		Size = UDim2.new(0.4, 0, 1, 0),
		Position = UDim2.fromScale(0.6, 0),
		TextXAlignment = Enum.TextXAlignment.Right,
	})

	label(`Участники: {#clan.members}/{Config.MaxMembers}`, row(22), { TextSize = 13 })

	local list = make("ScrollingFrame", {
		Size = UDim2.fromScale(1, 1),
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		ScrollBarThickness = 6,
		CanvasSize = UDim2.new(),
		AutomaticCanvasSize = Enum.AutomaticSize.Y,
	}, row(210))
	make("UIListLayout", { Padding = UDim.new(0, 4) }, list)
	for _, member in clan.members do
		buildMemberRow(list, member, myRank)
	end

	if myRank >= Ranks.Officer then
		local inviteRow = row(34)
		textBox("Ник игрока на сервере", draft.invite, inviteRow, { Size = UDim2.new(1, -118, 1, 0) }, function(text)
			draft.invite = text
		end)
		button("Пригласить", ACCENT, inviteRow, {
			Size = UDim2.new(0, 110, 1, 0),
			Position = UDim2.new(1, -110, 0, 0),
		}, function()
			if call("Invite", draft.invite) then
				draft.invite = ""
			end
		end)
	end

	if myRank == Ranks.Owner then
		confirmButton("Распустить клан", DANGER, row(34), nil, function()
			call("Disband")
		end)
	else
		confirmButton("Покинуть клан", DANGER, row(34), nil, function()
			call("Leave")
		end)
	end
end

local function rebuild()
	content:ClearAllChildren()
	rowOrder = 0
	make("UIListLayout", { Padding = UDim.new(0, 6), SortOrder = Enum.SortOrder.LayoutOrder }, content)
	if state.clan then
		buildClan(state.clan)
	else
		buildNoClan()
	end
end

stateChanged.OnClientEvent:Connect(function(newState)
	state = newState
	rebuild()
end)

rebuild()
task.spawn(function()
	-- Сервер может ещё загружать данные игрока, поэтому запрашиваем состояние до успеха.
	for _ = 1, 10 do
		local ok, success = pcall(request.InvokeServer, request, "GetState")
		if ok and success then
			break
		end
		task.wait(1)
	end
end)

---------------------------------------------------------------------
-- Индикатор захвата
---------------------------------------------------------------------

local hud = make("Frame", {
	Size = UDim2.fromOffset(300, 56),
	AnchorPoint = Vector2.new(0.5, 0),
	Position = UDim2.new(0.5, 0, 0, 70),
	BackgroundColor3 = BACKGROUND,
	BackgroundTransparency = 0.2,
	BorderSizePixel = 0,
	Visible = false,
}, gui)
round(hud)

local hudTitle = label("", hud, {
	Size = UDim2.new(1, -16, 0, 30),
	Position = UDim2.fromOffset(8, 2),
	TextXAlignment = Enum.TextXAlignment.Center,
	Font = Enum.Font.GothamMedium,
})
local hudBar = make("Frame", {
	Size = UDim2.new(1, -16, 0, 12),
	Position = UDim2.fromOffset(8, 36),
	BackgroundColor3 = PANEL,
	BorderSizePixel = 0,
}, hud)
local hudFill = make("Frame", { Size = UDim2.fromScale(0, 1), BorderSizePixel = 0 }, hudBar)

local function updateHud()
	local zoneName = player:GetAttribute("CurrentZone")
	local folder = Workspace:FindFirstChild(Config.ZonesFolderName)
	local part = zoneName and folder and folder:FindFirstChild(zoneName)
	if not part then
		hud.Visible = false
		return
	end

	local control = part:GetAttribute("Control") or 0
	local holderTag = part:GetAttribute("HolderTag")
	local ownerTag = part:GetAttribute("OwnerTag")
	local name = part:GetAttribute("ZoneName") or part.Name

	local text
	if part:GetAttribute("Contested") then
		text = "зона оспаривается!"
	elseif ownerTag and control >= 1 then
		text = `под контролем [{ownerTag}]`
	elseif holderTag then
		text = `[{holderTag}] {math.floor(control * 100)}%`
	else
		text = "нейтральная"
	end
	hudTitle.Text = `{name}: {text}`
	hudFill.Size = UDim2.fromScale(control, 1)
	hudFill.BackgroundColor3 = part:GetAttribute("HolderColor") or Config.NeutralColor
	hud.Visible = true
end

task.spawn(function()
	while true do
		task.wait(0.1)
		updateHud()
	end
end)

zoneCaptured.OnClientEvent:Connect(function(zoneName, clanName, clanTag)
	pcall(function()
		StarterGui:SetCore("SendNotification", {
			Title = "Зона захвачена",
			Text = `[{clanTag}] {clanName} захватил «{zoneName}»`,
			Duration = 5,
		})
	end)
end)
