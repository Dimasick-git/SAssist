# SAssist protocol (v0)

JSON frames over WebSocket. One JSON object per frame, field `type` discriminates.

## client -> server
| type | fields | meaning |
|---|---|---|
| join | username | register, lands in #general |
| send | channel, text | post message (text may contain markdown / \`\`\` code blocks) |
| switchChannel | channel | change active channel, get its history |
| listChannels | — | request channel list |

## server -> client
| type | fields |
|---|---|
| welcome | userId, username, channels[] |
| message | message{ id, channel, username, text, ts } |
| presence | channel, users[] |
| history | channel, messages[] |
| channels | channels[] |
| error | reason |

Default channels: `general`, `code-help`, `showtime`. History: last 100 msgs/channel.
