# Basira OS — Design

Live canvas (editable): https://claude.ai/artifact/C73ujixf72FQPy7AjFm3PL

Screens are in `design/`:
- `design/png/` — renders of each screen (look at these first)
- `design/html/` — standalone HTML of each screen (open in a browser; exact sizes, colours, spacing)
- `design/source/` — original canvas files (`.dc.html` + `canvas.json`)

All screens are one eye's view, 1280×720 landscape (what the user sees through one lens).

## Identity
- Name: **بصيرة** / **Basira OS** — "vision + insight": it sees the room and understands the screen.
- Tagline: «شاشتك صارت بحجم غرفتك.»
- Wake word: «يا بصيرة» (also "Hey Basira")
- Logo: an eye shape with a filled pupil, dark on an emerald rounded square.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| emerald | `#10B981` | primary actions, active dock item, assistant orb |
| mint | `#6EE7B7` | accent text, labels on dark |
| night | `#0B0F0D` | brand backgrounds |
| glass | `rgba(14,18,16,0.78)` + blur 18–24 | all floating panels |
| glass-border | `rgba(255,255,255,0.12)` 1 px | panel outlines |
| text | `#F4F7F5` | primary text |
| text-muted | `#B8C4BE` / `#9FB0A7` | secondary text |
| warning | `#FBBF24` | thermal indicator |
| live | `#F87171` | listening dot |

Font: IBM Plex Sans Arabic 400/500/600/700. Sizes: 30 greeting, 26 window title, 16–17 body/labels, 12–13 captions.
Radii: windows 26, small panels 22, home grid 34, dock 28, dock buttons 18, pills 999.

## Components
- **Status pills (top centre):** time, Wi-Fi, Basira state eye; separate thermal pill «38° وضع التبريد» when not normal.
- **Window:** glass panel; top bar = back, URL pill with lock, options; content; grab bar (120×7, white 75%) centred under the window.
- **Page-understanding chip:** inside the window bottom, emerald tint 14% + emerald border 35%: «بصيرة قرأت الصفحة: … بدك ملخص بالعربي؟» + «لخّص» button.
- **Side windows:** angled ~24° toward the user (perspective), e.g. video on the right, pinned tasks widget on the left labelled «مثبّتة على الحائط».
- **Cursor:** white ring 46 px with emerald halo; inner emerald dot = pinch state. Hint label «قرصة = نقرة · اسحب لتحريك».
- **Assistant bar:** above the dock; emerald orb with glow, red live dot + «بصيرة بتسمعك», transcript in quotes, waveform bars.
- **Dock (bottom centre):** Home, Mic (emerald when active), View mode (eye), Open windows. 52 px buttons.
- **Home:** greeting «مسا الخير يا يزن», 6×2 grid of 72 px circular app icons, saved spaces cards («غرفة الدراسة», «الصالون — سينما»), a Basira suggestion card («وقفت عند الدقيقة ١٢… نكمّل؟»).

App icons in the prototype are placeholders (letters/glyphs). Do not use other companies' logos in the shipped UI; use the site's own favicon fetched at runtime or a neutral glyph.
