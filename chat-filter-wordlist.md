# Minecraft 聊天屏蔽词 · 可编辑词表

> 含脏话、骚扰和仇恨用语，仅供个人聊天过滤。476 条候选随包内置并默认开启；下面的复选框留给你制作自己的屏蔽本，不代表运行中规则的开关。

## 怎么改

想导入自定义条目时，把需要的行摘到新的 UTF-8 `.md` 文件：每行一条；普通 `- 词条` 和 `- [x]` 启用，`- [ ]` 导入为停用。加上 `— 遮词`、`— 隐藏整句` 或 `— 替换为：你的文字` 可指定动作。标注说明和反例时，用普通段落或引用，不要用以 `- ` 开头的行。

内置短缩写有防误伤的整句规则。自制 Markdown 则通常按字面片段导入；复杂规则（例如要指定整句）请用 [README 里的 JSON 格式](<README.md#json>)。现版整句与片段区分大小写，每条聊天只执行第一条匹配的启用规则；`L/l` 刷屏是特例。当前最多 **2048 条规则（含已有规则）**。

## 原有的 4 条内置规则

| 匹配内容 | 现有方式和动作 |
|---|---|
| `L`、`llll`、`LLLL!!!@!@!@` 等 | `刷 L`：仅由 L/l、空白、标点组成，至少有一个 L/l；整条替换为 `love you`。不是屏蔽单词里的 L。 |
| `ez` | 字面片段替换为 `gg`；可能误伤用户名或单词，建议后续考虑整句。 |
| `noob` | 字面片段遮为 `***`；会匹配包含它的更长文本。 |
| `垃圾队友` | 仅整句时隐藏。 |

## 1. 中文直骂与常见变体

建议优先选较明确的片段；极短拼音见第 7 节。

- [ ] `傻逼`
- [ ] `煞笔`
- [ ] `傻比`
- [ ] `傻B`
- [ ] `沙比`
- [ ] `沙壁`
- [ ] `傻批`
- [ ] `死傻逼`
- [ ] `傻卵`
- [ ] `傻吊`
- [ ] `傻屌`
- [ ] `屌毛`
- [ ] `狗屎`
- [ ] `吃屎去`
- [ ] `滚去吃屎`
- [ ] `你是坨屎`
- [ ] `你就一坨屎`
- [ ] `他妈的`
- [ ] `她妈的`
- [ ] `你妈的`
- [ ] `他马的`
- [ ] `尼玛的`
- [ ] `踏马的`
- [ ] `你特么`
- [ ] `你特喵`
- [ ] `干你娘`
- [ ] `幹你娘`
- [ ] `干你老母`
- [ ] `操你`
- [ ] `草你妈`
- [ ] `艹你妈`
- [ ] `肏你妈`
- [ ] `日你妈`
- [ ] `我操你妈`
- [ ] `操尼玛`
- [ ] `草泥马`
- [ ] `我去你妈的`
- [ ] `去你妈的`
- [ ] `滚你妈的`
- [ ] `死开`
- [ ] `快滚吧`
- [ ] `滚蛋`
- [ ] `滚犊子`
- [ ] `滚一边去`
- [ ] `有多远滚多远`
- [ ] `给爷爬`
- [ ] `你配吗`
- [ ] `你算个屁`
- [ ] `你算个什么东西`
- [ ] `你个屁`
- [ ] `你脑子有病`
- [ ] `脑子进水`
- [ ] `脑子被驴踢了`
- [ ] `脑子被门夹了`
- [ ] `出门没带脑子`
- [ ] `智商欠费`
- [ ] `智障玩意`
- [ ] `脑残玩意`
- [ ] `低能玩意`
- [ ] `废物东西`
- [ ] `死废物`
- [ ] `一群废物`
- [ ] `呆逼`
- [ ] `蠢货`
- [ ] `蠢驴`
- [ ] `蠢猪`
- [ ] `蠢狗`
- [ ] `狗东西`
- [ ] `狗杂种`
- [ ] `狗崽子`
- [ ] `狗娘养的`
- [ ] `人渣`
- [ ] `渣滓`
- [ ] `畜生东西`
- [ ] `真是个畜生`
- [ ] `贱货`
- [ ] `小贱人`
- [ ] `贱种`
- [ ] `死贱人`
- [ ] `婊子`
- [ ] `臭婊子`
- [ ] `婊子养的`
- [ ] `臭不要脸`
- [ ] `死不要脸`
- [ ] `恶心死了`
- [ ] `看见你就恶心`
- [ ] `墙都不扶就服你`

## 2. 针对家人、羞辱和骚扰

建议用完整短语，不要屏蔽单独的「妈」「爸」「孤儿」「家庭」等词。

- [ ] `你妈没了`
- [ ] `你妈死了`
- [ ] `你马没了`
- [ ] `你爸没了`
- [ ] `你爹没了`
- [ ] `你爹死了`
- [ ] `你全家没了`
- [ ] `你全家死了`
- [ ] `死全家`
- [ ] `全家升天`
- [ ] `全家都去死`
- [ ] `全家暴毙`
- [ ] `全家火葬场`
- [ ] `没爹没妈的东西`
- [ ] `没妈的东西`
- [ ] `没妈玩意`
- [ ] `没人教你做人`
- [ ] `没人要的东西`
- [ ] `有娘生没娘养`
- [ ] `有妈生没妈教`
- [ ] `开局先问候你全家`
- [ ] `你是孤儿吧`
- [ ] `孤儿玩意`
- [ ] `你个孤儿`
- [ ] `你个杂种`
- [ ] `你祖坟`
- [ ] `你家祖坟`
- [ ] `你家户口本`
- [ ] `户口本只剩你`
- [ ] `户口本就你一个`
- [ ] `叫爸爸`
- [ ] `给我跪下`
- [ ] `给爷跪下`
- [ ] `给爷磕头`
- [ ] `跪下认错`
- [ ] `你根本不配做人`
- [ ] `全服都讨厌你`
- [ ] `没人想和你玩`
- [ ] `滚出这个服`
- [ ] `滚出这个服务器`
- [ ] `你别再出现了`
- [ ] `脸都不要了`
- [ ] `给我闭嘴`
- [ ] `闭上你的臭嘴`
- [ ] `别狗叫`
- [ ] `不服来单挑`
- [ ] `说话像个小学生`

## 3. Minecraft 对局嘲讽、甩锅和挂人

有些也是玩笑或队友复盘，先选经常遇到的；不要单独屏蔽「菜」「挂」「举报」「作弊」。

- [ ] `菜就多练`
- [ ] `菜得抠脚`
- [ ] `菜得离谱`
- [ ] `菜得要死`
- [ ] `菜得没眼看`
- [ ] `菜得跟狗一样`
- [ ] `菜成这样`
- [ ] `菜成一坨`
- [ ] `菜鸡互啄`
- [ ] `菜狗`
- [ ] `死菜鸡`
- [ ] `菜逼`
- [ ] `纯菜狗`
- [ ] `你是真菜`
- [ ] `你是真废`
- [ ] `不会玩别玩`
- [ ] `不会打就滚`
- [ ] `别来丢人`
- [ ] `丢人现眼`
- [ ] `别打了卸载吧`
- [ ] `回家种地去`
- [ ] `赶紧卸载游戏`
- [ ] `卸载吧`
- [ ] `别玩这个游戏了`
- [ ] `没赢过吗`
- [ ] `就这水平`
- [ ] `你也就这点本事`
- [ ] `比人机还菜`
- [ ] `不如人机`
- [ ] `人机水平`
- [ ] `白给怪`
- [ ] `你只会送`
- [ ] `你又送`
- [ ] `跟你打必输`
- [ ] `队友全是废物`
- [ ] `队友都是人机`
- [ ] `狗屎队友`
- [ ] `废物队友`
- [ ] `猪队友`
- [ ] `坑货队友`
- [ ] `被队友坑死了`
- [ ] `拖后腿的`
- [ ] `你就是来送的`
- [ ] `你就是个演员`
- [ ] `玩不起别玩`
- [ ] `输不起就滚`
- [ ] `小学生滚`
- [ ] `耍赖狗`
- [ ] `挂狗`
- [ ] `开挂狗`
- [ ] `开挂孤儿`
- [ ] `开挂死全家`
- [ ] `挂逼`
- [ ] `挂比`
- [ ] `作弊狗`
- [ ] `你个作弊狗`
- [ ] `开了还装`
- [ ] `开挂还嘴硬`
- [ ] `开挂还打不过`
- [ ] `全服举报他`
- [ ] `都去举报他`
- [ ] `有本事别开挂`
- [ ] `不举报你跟你姓`

## 4. 定向威胁、隐私骚扰和自伤怂恿

只挑指向明确的语句；正常讨论自伤、求助、举报或游戏内打怪不该被误屏蔽。重要证据可先保留原文，不必整行隐藏。

- [ ] `你给我去死`
- [ ] `怎么还没去死`
- [ ] `你怎么不去死`
- [ ] `你去死吧`
- [ ] `赶紧去死`
- [ ] `滚去死`
- [ ] `死一边去`
- [ ] `你不配活着`
- [ ] `活着浪费空气`
- [ ] `你不如死了算了`
- [ ] `去自杀`
- [ ] `你就该自杀`
- [ ] `赶紧自杀`
- [ ] `去跳楼`
- [ ] `你怎么还不跳楼`
- [ ] `我去你家打你`
- [ ] `我去你家找你算账`
- [ ] `我就在你家楼下等你`
- [ ] `线下等着挨打`
- [ ] `现实中弄死你`
- [ ] `现实里弄死你`
- [ ] `现实里我会杀了你`
- [ ] `找人打断你的腿`
- [ ] `要打断你的腿`
- [ ] `我去砸你家`
- [ ] `我要发你住址`
- [ ] `我把你住址挂出来`
- [ ] `我把你身份证发出来`
- [ ] `给你开盒`
- [ ] `我把你开了盒`
- [ ] `马上开你盒`
- [ ] `我要让全服知道你电话`
- [ ] `我要曝光你的隐私`
- [ ] `等着被网暴`
- [ ] `你等着被人肉`
- [ ] `我把你人肉出来`

## 5. 英文辱骂与对局嘲讽

多词短语通常更适合做片段。单词建议整词或整句，不要直接用子串。`gg`、`wp`、`skill`、`bot`、`ez` 在普通语境或词内部都可能误伤。

- [ ] `fuck you`
- [ ] `fuck off`
- [ ] `go fuck yourself`
- [ ] `get the fuck out`
- [ ] `shut the fuck up`
- [ ] `who the fuck asked`
- [ ] `you fucking suck`
- [ ] `fucking idiot`
- [ ] `fucking moron`
- [ ] `fucking loser`
- [ ] `fucking noob`
- [ ] `fucking trash`
- [ ] `fucking clown`
- [ ] `dumb as fuck`
- [ ] `trash as fuck`
- [ ] `piece of shit`
- [ ] `eat shit`
- [ ] `go eat shit`
- [ ] `you little shit`
- [ ] `you are shit`
- [ ] `you're shit`
- [ ] `shit player`
- [ ] `shit team`
- [ ] `shit teammate`
- [ ] `son of a bitch`
- [ ] `little bitch`
- [ ] `cry like a bitch`
- [ ] `you stupid bitch`
- [ ] `bitch ass`
- [ ] `asshole`
- [ ] `motherfucker`
- [ ] `dumbass`
- [ ] `dipshit`
- [ ] `shithead`
- [ ] `dickhead`
- [ ] `douchebag`
- [ ] `jackass`
- [ ] `dumbfuck`
- [ ] `brain-dead idiot`
- [ ] `brain dead idiot`
- [ ] `absolute moron`
- [ ] `absolute idiot`
- [ ] `complete idiot`
- [ ] `you are a moron`
- [ ] `you're a moron`
- [ ] `you are an idiot`
- [ ] `you're an idiot`
- [ ] `learn to play`
- [ ] `uninstall the game`
- [ ] `uninstall minecraft`
- [ ] `please uninstall`
- [ ] `quit the game`
- [ ] `go back to creative`
- [ ] `you are trash`
- [ ] `you're trash`
- [ ] `ur trash`
- [ ] `trash player`
- [ ] `such a noob`
- [ ] `noob team`
- [ ] `noob teammate`
- [ ] `pathetic loser`
- [ ] `huge loser`
- [ ] `get good`
- [ ] `git gud`
- [ ] `get rekt`
- [ ] `you got rekt`
- [ ] `get wrecked`
- [ ] `sit down kid`
- [ ] `you play like a bot`
- [ ] `worse than a bot`
- [ ] `boosted noob`
- [ ] `boosted loser`
- [ ] `carried noob`
- [ ] `you got carried`
- [ ] `zero skill`
- [ ] `no skill`
- [ ] `skill issue`
- [ ] `imagine losing`
- [ ] `stay mad`
- [ ] `cry more`
- [ ] `cope harder`
- [ ] `seethe harder`
- [ ] `hold this L`
- [ ] `take the L`
- [ ] `gg ez`
- [ ] `ez win`
- [ ] `easy win noobs`
- [ ] `too easy noobs`
- [ ] `ez clap`
- [ ] `free win noobs`
- [ ] `hacking loser`
- [ ] `cheating loser`
- [ ] `cheating scum`
- [ ] `dirty cheater`
- [ ] `report this clown`
- [ ] `everyone report him`
- [ ] `shut up kid`
- [ ] `nobody likes you`
- [ ] `nobody wants you here`
- [ ] `leave the server loser`

## 6. 英文威胁、仇恨辱称与擦边/诈骗引流

优先选定向用语。常见仇恨辱称单列在这里，仅用于识别过滤；身份名称本身不加入词表。广告短语也可能出现在举报或说明里。

- [ ] `kill yourself`
- [ ] `go kill yourself`
- [ ] `you should kill yourself`
- [ ] `go die`
- [ ] `go die in a hole`
- [ ] `i hope you die`
- [ ] `you deserve to die`
- [ ] `nobody cares if you die`
- [ ] `i will kill you in real life`
- [ ] `i'll kill you irl`
- [ ] `i know where you live`
- [ ] `i will dox you`
- [ ] `i'll doxx you`
- [ ] `i'll leak your address`
- [ ] `i'll leak your phone number`
- [ ] `your whole family should die`
- [ ] `i hope your family dies`
- [ ] `faggot`
- [ ] `tranny`
- [ ] `retard`
- [ ] `nigger`
- [ ] `chink`
- [ ] `kike`
- [ ] `spic`
- [ ] `gook`
- [ ] `dyke`
- [ ] `coon`
- [ ] `支那猪`
- [ ] `日本鬼子滚出去`
- [ ] `黑鬼滚`
- [ ] `死基佬`
- [ ] `同性恋都去死`
- [ ] `send nudes`
- [ ] `send me nudes`
- [ ] `show me your nudes`
- [ ] `dm me for nudes`
- [ ] `加我发裸照`
- [ ] `给我发裸照`
- [ ] `发你裸照`
- [ ] `加我看黄图`
- [ ] `私聊发黄图`
- [ ] `想看黄图加我`
- [ ] `免费领正版账号`
- [ ] `免费领微软正版账号`
- [ ] `免费代领披风`
- [ ] `扫码免费领披风`
- [ ] `提供验证码领披风`
- [ ] `发验证码领取`
- [ ] `把登录验证码发我`
- [ ] `把微软验证码发我`
- [ ] `把微软账号密码发我`
- [ ] `私聊发送账号密码`
- [ ] `扫码登录领取皮肤`
- [ ] `click here for free minecoins`
- [ ] `send me your microsoft login`
- [ ] `send me the verification code`
- [ ] `send me your recovery code`
- [ ] `free cape if you verify`
- [ ] `free minecraft account generator`
- [ ] `free minecoins generator`
- [ ] `scan this qr code for a free cape`

## 7. 短缩写、拼音和高误伤候选：仅考虑整句

例如 `sb` 也可能指 SkyBlock，`cnm` 可能是用户名；`nt` 常指 nice try，`bot` 可指机器人，`L` 不应变成字面片段。下面都不是默认规则。

- [ ] `SB`
- [ ] `sb`
- [ ] `s b`
- [ ] `s.b`
- [ ] `s*b`
- [ ] `2b`
- [ ] `250`
- [ ] `cnm`
- [ ] `CNM`
- [ ] `cnmd`
- [ ] `nmsl`
- [ ] `NMSL`
- [ ] `n m s l`
- [ ] `tmd`
- [ ] `tm的`
- [ ] `md`
- [ ] `wdnmd`
- [ ] `wcnm`
- [ ] `gtnnd`
- [ ] `nmgb`
- [ ] `nbm`
- [ ] `zz`
- [ ] `nt`
- [ ] `lj`
- [ ] `fw`
- [ ] `爬`
- [ ] `滚`
- [ ] `呵呵`
- [ ] `就这`
- [ ] `急了`
- [ ] `破防了`
- [ ] `典`
- [ ] `笑死`
- [ ] `小丑`
- [ ] `菜鸡`
- [ ] `废物`
- [ ] `蠢逼`
- [ ] `辣鸡`
- [ ] `乐色`
- [ ] `kys`
- [ ] `KYS`
- [ ] `k y s`
- [ ] `k.y.s`
- [ ] `stfu`
- [ ] `STFU`
- [ ] `s t f u`
- [ ] `gtfo`
- [ ] `GTFO`
- [ ] `fu`
- [ ] `f u`
- [ ] `f*ck`
- [ ] `f u c k`
- [ ] `idgaf`
- [ ] `fk u`
- [ ] `fck u`
- [ ] `fk off`
- [ ] `kma`
- [ ] `smd`
- [ ] `l2p`
- [ ] `bad`
- [ ] `loser`
- [ ] `clown`
- [ ] `noobs`
- [ ] `n00b`
- [ ] `n o o b`
- [ ] `trash`
- [ ] `bot`
- [ ] `dogwater`
- [ ] `dog water`
- [ ] `b0t`
- [ ] `smurf`
- [ ] `garbage`
- [ ] `rekt`
- [ ] `bozo`
- [ ] `ezpz`
- [ ] `e z`
- [ ] `ezz`
- [ ] `ezzz`
- [ ] `ggez`
- [ ] `GG EZ`
- [ ] `ez pz`
- [ ] `EZZZ`

## 8. 误伤检查样本：应保留

> `Hello`、`LOL`、`L1`、`去 L 层`、`我按了 L`：不是单独的刷 L。
>
> `SkyBlock 缩写 SB`、`nt，差一点`、`gg wp`、`nice try`、`这个机器人是 bot`：不能见短缩写就遮。
>
> `我的账号叫 Breeze`、`I sneezed`：检查 `ez` 在普通名字和单词内部的误伤。
>
> `他发了威胁我需要举报`、`不要把验证码发给任何人`、`有自伤风险可以求助`：正常提醒和求助不是攻击。
>
> `qaz 说他准备好了`：只是正文提到账号，不能给发话的人加 qaz 的标签。qaz 只是虚拟的账号，并非实际账号，tag 默认当前账号。

## 我想补的词 / 替换为

<!-- 可以照抄 `- [ ] \`某个具体短语\` — 遮词`，或直接写中文备注。 -->
