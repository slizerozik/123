-- Кланы: создание, приглашения, ранги, очки, сохранение в DataStore.
local DataStoreService = game:GetService("DataStoreService")
local HttpService = game:GetService("HttpService")
local MessagingService = game:GetService("MessagingService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local TextService = game:GetService("TextService")

local Config = require(ReplicatedStorage:WaitForChild("ClanShared"):WaitForChild("Config"))
local Ranks = Config.Ranks

local SYNC_TOPIC = "ClanSync"
local POINTS_FLUSH_INTERVAL = 60
local REQUEST_COOLDOWN = 0.3

local ClanService = {}

local clans = {} -- clanId -> данные клана (только кланы с игроками на этом сервере)
local playerClan = {} -- Player -> clanId
local invites = {} -- Player -> { [clanId] = { expires, name, tag, from } }
local pendingPoints = {} -- clanId -> ещё не сохранённые очки
local loaded = {} -- Player -> true, когда данные игрока загружены
local busy = {} -- Player -> true, пока выполняется запрос
local lastRequest = {} -- Player -> os.clock()

local removedEvent = Instance.new("BindableEvent")
ClanService.ClanRemoved = removedEvent.Event -- (clanId)

local stateRemote

---------------------------------------------------------------------
-- Хранилище
---------------------------------------------------------------------

local function deepCopy(value)
	if type(value) ~= "table" then
		return value
	end
	local copy = {}
	for k, v in value do
		copy[k] = deepCopy(v)
	end
	return copy
end

local function attempt(fn)
	local lastError
	for i = 1, 3 do
		local ok, result = pcall(fn)
		if ok then
			return true, result
		end
		lastError = result
		if RunService:IsStudio() then
			break
		end
		task.wait(i)
	end
	return false, lastError
end

-- В Studio без доступа к API DataStore падает, тогда данные живут в памяти до конца сессии.
local function makeStore(name)
	local memory = {}
	local useMemory = false
	local dataStore

	local function run(real, fallback)
		if not useMemory then
			local ok, result = attempt(function()
				dataStore = dataStore or DataStoreService:GetDataStore(name)
				return real(dataStore)
			end)
			if ok then
				return true, result
			end
			if not RunService:IsStudio() then
				warn(`[Clans] DataStore {name}: {result}`)
				return false, nil
			end
			useMemory = true
			warn(`[Clans] DataStore {name} недоступен, данные не сохранятся: {result}`)
		end
		return true, fallback()
	end

	local store = {}

	function store.Get(key)
		return run(function(ds)
			return (ds:GetAsync(key))
		end, function()
			return deepCopy(memory[key])
		end)
	end

	function store.Update(key, fn)
		return run(function(ds)
			return (ds:UpdateAsync(key, fn))
		end, function()
			local value = fn(deepCopy(memory[key]))
			if value ~= nil then
				memory[key] = value
			end
			return value
		end)
	end

	function store.Remove(key)
		return run(function(ds)
			return (ds:RemoveAsync(key))
		end, function()
			memory[key] = nil
			return nil
		end)
	end

	return store
end

local clanStore = makeStore("Clans_v1")
local nameStore = makeStore("ClanNames_v1")
local playerStore = makeStore("ClanPlayers_v1")

---------------------------------------------------------------------
-- Состояние игроков
---------------------------------------------------------------------

local function rankOf(clan, userId)
	local member = clan.members[tostring(userId)]
	return member and member.rank or 0
end

local function memberCount(clan)
	local count = 0
	for _ in clan.members do
		count += 1
	end
	return count
end

local function clanView(clan)
	local members = {}
	for userId, member in clan.members do
		local id = tonumber(userId)
		table.insert(members, {
			userId = id,
			name = member.name,
			rank = member.rank,
			online = Players:GetPlayerByUserId(id) ~= nil,
		})
	end
	table.sort(members, function(a, b)
		if a.rank ~= b.rank then
			return a.rank > b.rank
		end
		return a.name < b.name
	end)
	return {
		id = clan.id,
		name = clan.name,
		tag = clan.tag,
		color = clan.color,
		points = clan.points + (pendingPoints[clan.id] or 0),
		members = members,
	}
end

local function pushState(player)
	if player.Parent ~= Players then
		return
	end
	local clan = clans[playerClan[player]]
	local inviteList = {}
	local now = os.time()
	for clanId, invite in invites[player] or {} do
		if invite.expires > now then
			table.insert(inviteList, { clanId = clanId, name = invite.name, tag = invite.tag, from = invite.from })
		end
	end
	stateRemote:FireClient(player, {
		clan = clan and clanView(clan) or nil,
		invites = inviteList,
	})
end

local function updateTag(player)
	local character = player.Character
	local head = character and character:FindFirstChild("Head")
	if not head then
		return
	end
	local old = head:FindFirstChild("ClanTag")
	if old then
		old:Destroy()
	end
	local clan = clans[playerClan[player]]
	if not clan then
		return
	end

	local gui = Instance.new("BillboardGui")
	gui.Name = "ClanTag"
	gui.Size = UDim2.fromOffset(120, 22)
	gui.StudsOffset = Vector3.new(0, 2.6, 0)
	gui.MaxDistance = 80

	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.Font = Enum.Font.GothamBold
	label.TextScaled = true
	label.TextStrokeTransparency = 0.4
	label.TextColor3 = Config.Colors[clan.color] or Config.Colors[1]
	label.Text = `[{clan.tag}]`
	label.Parent = gui

	gui.Parent = head
end

-- Не yield-ит: запись в хранилище уходит в отдельный поток.
local function setPlayerClan(player, clanId, skipSave)
	playerClan[player] = clanId
	local clan = clans[clanId]
	player:SetAttribute("ClanId", clanId)
	player:SetAttribute("ClanName", clan and clan.name or nil)
	player:SetAttribute("ClanTag", clan and clan.tag or nil)
	updateTag(player)
	pushState(player)

	if not skipSave then
		local key = tostring(player.UserId)
		task.spawn(function()
			if clanId then
				playerStore.Update(key, function()
					return clanId
				end)
			else
				playerStore.Remove(key)
			end
		end)
	end
end

local function onlineMembers(clanId)
	local list = {}
	for player, id in playerClan do
		if id == clanId then
			table.insert(list, player)
		end
	end
	return list
end

-- Приводит игроков сервера в соответствие с clans[clanId] и рассылает им состояние.
local function sync(clanId)
	local clan = clans[clanId]
	for _, player in onlineMembers(clanId) do
		if clan and clan.members[tostring(player.UserId)] then
			setPlayerClan(player, clanId, true)
		else
			setPlayerClan(player, nil)
		end
	end
end

local function publish(clanId, deleted)
	task.spawn(pcall, function()
		MessagingService:PublishAsync(SYNC_TOPIC, { id = clanId, job = game.JobId, deleted = deleted })
	end)
end

-- Атомарно меняет клан в хранилище. fn(data) правит data и возвращает текст ошибки, чтобы отменить.
local function mutateClan(clanId, fn)
	local problem, result
	local ok = clanStore.Update(clanId, function(data)
		problem, result = nil, nil
		if not data then
			problem = "Клан не найден"
			return nil
		end
		problem = fn(data)
		if problem then
			return nil
		end
		result = data
		return data
	end)
	if not ok then
		return false, "Не удалось сохранить, попробуйте позже"
	end
	if problem then
		return false, problem
	end
	clans[clanId] = result
	sync(clanId)
	publish(clanId)
	return true
end

---------------------------------------------------------------------
-- Проверка текста
---------------------------------------------------------------------

local function cleanText(value, minLength, maxLength)
	if type(value) ~= "string" or #value > 100 then
		return nil
	end
	local text = value:gsub("%s+", " "):match("^ ?(.-) ?$")
	local length = utf8.len(text)
	if not length or length < minLength or length > maxLength or text:find("%c") then
		return nil
	end
	return text
end

local function passesFilter(text, userId)
	local ok, filtered = pcall(function()
		return TextService:FilterStringAsync(text, userId):GetNonChatStringForBroadcastAsync()
	end)
	return ok and filtered == text
end

local function findPlayer(query)
	if type(query) ~= "string" or query == "" or #query > 50 then
		return nil
	end
	query = query:lower()
	for _, player in Players:GetPlayers() do
		if player.Name:lower() == query then
			return player
		end
	end
	for _, player in Players:GetPlayers() do
		if player.Name:lower():sub(1, #query) == query or player.DisplayName:lower():sub(1, #query) == query then
			return player
		end
	end
	return nil
end

---------------------------------------------------------------------
-- Действия игроков (вызываются через RemoteFunction)
---------------------------------------------------------------------

local actions = {}

function actions.GetState(player)
	pushState(player)
	return true
end

function actions.Create(player, rawName, rawTag, colorIndex)
	if playerClan[player] then
		return false, "Вы уже состоите в клане"
	end
	local name = cleanText(rawName, Config.NameMinLength, Config.NameMaxLength)
	if not name then
		return false, `Название: от {Config.NameMinLength} до {Config.NameMaxLength} символов`
	end
	local tag = cleanText(rawTag, Config.TagMinLength, Config.TagMaxLength)
	if not tag or tag:find(" ") then
		return false, `Тег: от {Config.TagMinLength} до {Config.TagMaxLength} символов без пробелов`
	end
	if type(colorIndex) ~= "number" or not Config.Colors[colorIndex] then
		colorIndex = 1
	end
	if not passesFilter(name, player.UserId) or not passesFilter(tag, player.UserId) then
		return false, "Название или тег не прошли фильтр Roblox"
	end

	local clanId = HttpService:GenerateGUID(false)
	local nameKey = name:lower()
	local taken
	local ok = nameStore.Update(nameKey, function(existing)
		taken = existing ~= nil
		if taken then
			return nil
		end
		return clanId
	end)
	if not ok then
		return false, "Не удалось сохранить, попробуйте позже"
	end
	if taken then
		return false, "Клан с таким названием уже существует"
	end

	local clan = {
		id = clanId,
		name = name,
		tag = tag,
		color = colorIndex,
		points = 0,
		createdAt = os.time(),
		members = {
			[tostring(player.UserId)] = { name = player.Name, rank = Ranks.Owner },
		},
	}
	local saved = clanStore.Update(clanId, function()
		return clan
	end)
	if not saved then
		nameStore.Remove(nameKey)
		return false, "Не удалось сохранить, попробуйте позже"
	end

	clans[clanId] = clan
	invites[player] = nil
	setPlayerClan(player, clanId)
	return true, `Клан «{name}» создан`
end

function actions.Invite(player, query)
	local clan = clans[playerClan[player]]
	if not clan then
		return false, "Вы не в клане"
	end
	if rankOf(clan, player.UserId) < Ranks.Officer then
		return false, "Приглашать могут офицеры и лидер"
	end
	local target = findPlayer(query)
	if not target or target == player then
		return false, "Игрок не найден на сервере"
	end
	if playerClan[target] or not loaded[target] then
		return false, "Игрок уже в клане"
	end
	if memberCount(clan) >= Config.MaxMembers then
		return false, "Клан заполнен"
	end

	invites[target] = invites[target] or {}
	invites[target][clan.id] = {
		expires = os.time() + Config.InviteLifetime,
		name = clan.name,
		tag = clan.tag,
		from = player.Name,
	}
	pushState(target)
	return true, `Приглашение отправлено: {target.Name}`
end

function actions.AcceptInvite(player, clanId)
	if playerClan[player] then
		return false, "Вы уже состоите в клане"
	end
	local invite = type(clanId) == "string" and invites[player] and invites[player][clanId]
	if not invite or invite.expires <= os.time() then
		return false, "Приглашение истекло"
	end
	invites[player][clanId] = nil

	local key = tostring(player.UserId)
	local ok, problem = mutateClan(clanId, function(data)
		if not data.members[key] and memberCount(data) >= Config.MaxMembers then
			return "Клан заполнен"
		end
		data.members[key] = { name = player.Name, rank = Ranks.Member }
		return nil
	end)
	if not ok then
		pushState(player)
		return false, problem
	end

	invites[player] = nil
	setPlayerClan(player, clanId)
	sync(clanId)
	return true, `Вы вступили в «{invite.name}»`
end

function actions.DeclineInvite(player, clanId)
	if type(clanId) == "string" and invites[player] then
		invites[player][clanId] = nil
	end
	pushState(player)
	return true
end

function actions.Leave(player)
	local clanId = playerClan[player]
	if not clanId then
		return false, "Вы не в клане"
	end
	local key = tostring(player.UserId)
	local ok, problem = mutateClan(clanId, function(data)
		local member = data.members[key]
		if member and member.rank == Ranks.Owner then
			return "Лидер должен передать клан или распустить его"
		end
		data.members[key] = nil
		return nil
	end)
	if not ok then
		return false, problem
	end
	return true, "Вы покинули клан"
end

function actions.Kick(player, targetId)
	local clanId = playerClan[player]
	if not clanId or type(targetId) ~= "number" or targetId == player.UserId then
		return false, "Нельзя исключить"
	end
	local key = tostring(targetId)
	local ok, problem = mutateClan(clanId, function(data)
		local target = data.members[key]
		if not target then
			return "Игрок не в клане"
		end
		local myRank = rankOf(data, player.UserId)
		if myRank < Ranks.Officer or myRank <= target.rank then
			return "Недостаточно прав"
		end
		data.members[key] = nil
		return nil
	end)
	if not ok then
		return false, problem
	end
	return true, "Игрок исключён"
end

function actions.SetRank(player, targetId, rank)
	local clanId = playerClan[player]
	if not clanId or type(targetId) ~= "number" or targetId == player.UserId then
		return false, "Нельзя изменить ранг"
	end
	if rank ~= Ranks.Member and rank ~= Ranks.Officer then
		return false, "Неверный ранг"
	end
	local key = tostring(targetId)
	local ok, problem = mutateClan(clanId, function(data)
		if rankOf(data, player.UserId) ~= Ranks.Owner then
			return "Ранги меняет только лидер"
		end
		local target = data.members[key]
		if not target then
			return "Игрок не в клане"
		end
		target.rank = rank
		return nil
	end)
	if not ok then
		return false, problem
	end
	return true, "Ранг изменён"
end

function actions.Transfer(player, targetId)
	local clanId = playerClan[player]
	if not clanId or type(targetId) ~= "number" or targetId == player.UserId then
		return false, "Нельзя передать клан"
	end
	local key = tostring(targetId)
	local ok, problem = mutateClan(clanId, function(data)
		local me = data.members[tostring(player.UserId)]
		local target = data.members[key]
		if not me or me.rank ~= Ranks.Owner then
			return "Передать клан может только лидер"
		end
		if not target then
			return "Игрок не в клане"
		end
		target.rank = Ranks.Owner
		me.rank = Ranks.Officer
		return nil
	end)
	if not ok then
		return false, problem
	end
	return true, "Лидер клана изменён"
end

function actions.Disband(player)
	local clanId = playerClan[player]
	local clan = clans[clanId]
	if not clan then
		return false, "Вы не в клане"
	end
	if rankOf(clan, player.UserId) ~= Ranks.Owner then
		return false, "Распустить клан может только лидер"
	end
	if not clanStore.Remove(clanId) then
		return false, "Не удалось сохранить, попробуйте позже"
	end
	nameStore.Remove(clan.name:lower())

	clans[clanId] = nil
	pendingPoints[clanId] = nil
	sync(clanId)
	removedEvent:Fire(clanId)
	publish(clanId, true)
	return true, "Клан распущен"
end

local function onRequest(player, action, ...)
	local handler = type(action) == "string" and actions[action]
	if not handler then
		return false, "Неизвестное действие"
	end
	if not loaded[player] then
		return false, "Данные ещё загружаются"
	end
	local now = os.clock()
	if busy[player] or now - (lastRequest[player] or 0) < REQUEST_COOLDOWN then
		return false, "Слишком часто, подождите"
	end
	lastRequest[player] = now
	busy[player] = true
	local ok, success, message = pcall(handler, player, ...)
	busy[player] = nil
	if not ok then
		warn(`[Clans] Ошибка в {action}: {success}`)
		return false, "Внутренняя ошибка"
	end
	return success, message
end

---------------------------------------------------------------------
-- Вход и выход игроков
---------------------------------------------------------------------

local function onPlayerAdded(player)
	player.CharacterAdded:Connect(function(character)
		character:WaitForChild("Head", 10)
		updateTag(player)
	end)

	local key = tostring(player.UserId)
	local ok, clanId = playerStore.Get(key)
	if not ok then
		-- Не помечаем игрока загруженным: иначе он смог бы создать второй клан.
		warn(`[Clans] Не удалось загрузить данные {player.Name}`)
		return
	end

	if clanId then
		local clan = clans[clanId]
		if not clan then
			local found, data = clanStore.Get(clanId)
			if not found then
				warn(`[Clans] Не удалось загрузить клан {player.Name}`)
				return
			end
			-- Пока шёл запрос, клан мог загрузить другой игрок.
			clan = clans[clanId] or data
		end
		if player.Parent ~= Players then
			return
		end
		if clan and clan.members[key] then
			clans[clanId] = clan
			setPlayerClan(player, clanId, true)
			sync(clanId)
		else
			-- Исключили или клан распустили, пока игрока не было.
			setPlayerClan(player, nil)
		end
	end
	loaded[player] = true
	pushState(player)
end

local function onPlayerRemoving(player)
	local clanId = playerClan[player]
	playerClan[player] = nil
	invites[player] = nil
	loaded[player] = nil
	busy[player] = nil
	lastRequest[player] = nil
	if clanId then
		local others = onlineMembers(clanId)
		if #others == 0 then
			clans[clanId] = nil
		else
			-- Игрок ещё числится в Players, обновляем статус «онлайн» кадром позже.
			task.defer(function()
				for _, other in others do
					pushState(other)
				end
			end)
		end
	end
end

---------------------------------------------------------------------
-- Очки
---------------------------------------------------------------------

local function flushPoints()
	local batch = pendingPoints
	pendingPoints = {}
	for clanId, amount in batch do
		local ok, data = clanStore.Update(clanId, function(stored)
			if not stored then
				return nil
			end
			stored.points = (stored.points or 0) + amount
			return stored
		end)
		if not ok then
			pendingPoints[clanId] = (pendingPoints[clanId] or 0) + amount
		elseif data and clans[clanId] then
			clans[clanId].points = data.points
			for _, player in onlineMembers(clanId) do
				pushState(player)
			end
		end
	end
end

---------------------------------------------------------------------
-- Публичный API
---------------------------------------------------------------------

-- Возвращает данные клана игрока или nil. Таблицу не изменять.
function ClanService.GetPlayerClan(player)
	return clans[playerClan[player]]
end

function ClanService.GetClan(clanId)
	return clans[clanId]
end

-- Очки копятся в памяти и сохраняются пачкой раз в минуту.
function ClanService.AddPoints(clanId, amount)
	pendingPoints[clanId] = (pendingPoints[clanId] or 0) + amount
end

function ClanService.Init()
	local remotes = Instance.new("Folder")
	remotes.Name = "ClanRemotes"

	local requestRemote = Instance.new("RemoteFunction")
	requestRemote.Name = "Request"
	requestRemote.OnServerInvoke = onRequest
	requestRemote.Parent = remotes

	stateRemote = Instance.new("RemoteEvent")
	stateRemote.Name = "StateChanged"
	stateRemote.Parent = remotes

	remotes.Parent = ReplicatedStorage
	ClanService.Remotes = remotes

	Players.PlayerAdded:Connect(onPlayerAdded)
	Players.PlayerRemoving:Connect(onPlayerRemoving)
	for _, player in Players:GetPlayers() do
		task.spawn(onPlayerAdded, player)
	end

	-- Изменения клана на других серверах.
	task.spawn(pcall, function()
		MessagingService:SubscribeAsync(SYNC_TOPIC, function(message)
			local data = message.Data
			if type(data) ~= "table" or data.job == game.JobId then
				return
			end
			if data.deleted then
				clans[data.id] = nil
				pendingPoints[data.id] = nil
				sync(data.id)
				removedEvent:Fire(data.id)
			elseif clans[data.id] then
				local ok, fresh = clanStore.Get(data.id)
				if ok and clans[data.id] then
					clans[data.id] = fresh
					sync(data.id)
				end
			end
		end)
	end)

	task.spawn(function()
		while true do
			task.wait(POINTS_FLUSH_INTERVAL)
			flushPoints()
		end
	end)
	game:BindToClose(flushPoints)
end

return ClanService
