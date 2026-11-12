-- Общие настройки системы кланов и каптов (сервер и клиент).
local Config = {}

-- Кланы
Config.NameMinLength = 3
Config.NameMaxLength = 20
Config.TagMinLength = 2
Config.TagMaxLength = 5
Config.MaxMembers = 30
Config.InviteLifetime = 60 -- сколько секунд живёт приглашение

Config.Ranks = { Member = 1, Officer = 2, Owner = 3 }
Config.RankNames = { "Участник", "Офицер", "Лидер" }

Config.Colors = {
	Color3.fromRGB(231, 76, 60),
	Color3.fromRGB(230, 126, 34),
	Color3.fromRGB(241, 196, 15),
	Color3.fromRGB(46, 204, 113),
	Color3.fromRGB(26, 188, 156),
	Color3.fromRGB(52, 152, 219),
	Color3.fromRGB(155, 89, 182),
	Color3.fromRGB(236, 100, 170),
}

-- Капты
Config.ZonesFolderName = "CaptureZones" -- папка с зонами в Workspace
Config.CaptureTime = 30 -- секунд на полный захват одним игроком
Config.ExtraPlayerBonus = 0.5 -- прибавка к скорости за каждого следующего соклановца
Config.MaxSpeedMultiplier = 3
Config.DecayTime = 60 -- за сколько секунд пустая зона откатывает незавершённый захват
Config.TickInterval = 0.25
Config.ZoneHeight = 30 -- на сколько studs выше верха парта игрок ещё считается в зоне
Config.CaptureReward = 100 -- очки клану за захват
Config.HoldReward = 5 -- очки за удержание зоны...
Config.HoldRewardInterval = 30 -- ...каждые столько секунд
Config.RecolorZones = true -- красить парт зоны в цвет клана
Config.NeutralColor = Color3.fromRGB(160, 160, 160)

return Config
