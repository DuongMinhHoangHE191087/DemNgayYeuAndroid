# DESIGN.md - InLove (DemNgayYeuAndroid)

## 1. Visual World & Direction
- **Identity**: Dreamy Romantic Rose & Warm Pearl Velvet
- **Audience**: Couples celebrating love, young Gen-Z & Millennial partners who value beauty, romance, and visual clarity.
- **Mood**: Tender, sweet, emotionally evocative, clean, and modern.
- **Contrast Standard**: WCAG AAA for key metrics and headlines (>7:1); WCAG AA for secondary labels (>4.5:1).
- **Core Stance**: High contrast without harshness. Text is deep plum `#1F0416` and rich berry `#4A1934` on soft pearl white cards `#FFFFFF` / `#FFF5F8`.

---

## 2. Color Palette & Tokens

### Text & Content Contrast
| Token | Hex Value | Role & Usage | Contrast Ratio |
|---|---|---|---|
| `TextDarkPlum` | `#1F0416` | Main days count, couple names, primary headings | ~15:1 on white (WCAG AAA) |
| `TextSubtlePlum`| `#4A1934` | Subtitles, helper text, date badges, hints | ~9.2:1 on white (WCAG AAA) |
| `OnSurface` | `#1F0416` | Standard M3 text color on surfaces | ~15:1 on white |
| `OnSurfaceVariant` | `#4A1934`| M3 secondary text color | ~9.2:1 on white |

### Romantic Accents
| Token | Hex Value | Role & Usage |
|---|---|---|
| `Primary` | `#E91E63` | Romantic signature pink, icons, CTAs |
| `PrimaryContainer` | `#FF2D75` | Glowing heart badges, active indicators |
| `Secondary` | `#FF4081` | Accent badges, countdown progress |
| `HotPink` | `#FF1493` | Pulsating hearts, micro-interaction highlights |
| `RoseGradientStart` | `#FF2D75` | Linear gradient start |
| `RoseGradientMid` | `#E91E63` | Linear gradient midpoint |
| `RoseGradientEnd` | `#FF659A` | Linear gradient finish |

### Surfaces & Containers
| Token | Hex Value | Role & Usage |
|---|---|---|
| `Surface` | `#FFF5F8` | Screen default background (soft blush) |
| `SurfaceBright` | `#FFFFFF` | Card background (elevated pearl white) |
| `CardBorder` | `#FFC6DB` | Delicate romantic border for cards (1.2dp) |
| `SurfaceContainerLow` | `#FFF0F5` | Secondary pill tags and streak badges |

---

## 3. Typography Hierarchy
- **Days In Love Counter**: ExtraBold 46sp–54sp (`TextDarkPlum` `#1F0416`), line height 48sp–56sp.
- **Section Headers**: Bold 16sp–18sp (`TextDarkPlum` `#1F0416`), letter spacing 0.5sp.
- **Category Tags / Overlines**: ExtraBold 11sp (`Primary` `#E91E63`), letter spacing 0.8sp–1.0sp.
- **Card Subtitles & Hints**: Medium 11sp–12sp (`TextSubtlePlum` `#4A1934`).
- **Pill Badges**: Bold 10sp–11sp with padding 6dp–10dp horizontal, 2dp–4dp vertical.

---

## 4. Touch Targets & Spacing (Mobile Ergonomics)
- **Minimum Touch Target**: 48dp × 48dp on all interactive icons, quick buttons, and tabs.
- **Spacing Between Targets**: Minimum 8dp gap to prevent accidental taps.
- **Thumb Zone Placement**:
  - Primary bottom navigation within comfortable thumb reach (height 56dp, padding bottom for navigation bar).
  - Quick action bar and anniversary card actions clearly elevated with visible hit areas.
- **Corner Radii**:
  - Cards: 24dp – 28dp (soft, rounded, modern feel).
  - Badges & Pills: 50dp (stadium shape).
  - Buttons: 14dp – 50dp.

---

## 5. Micro-Interactions & Animation
- **Heartbeat Rhythm**: Dual expanding ripple waves (1.0x to 1.75x) with smooth linear easing simulating an authentic heartbeat.
- **Milestone Progress**: Smooth bouncy spring animation (`Spring.DampingRatioMediumBouncy`) when love days load or update.
- **Drifting Hearts**: Ambient floating pastel heart canvas drifting subtly in the background without obstructing readability.
- **Tactile Feedback**: Floating heart celebration burst and immediate toast confirmation on anniversary milestones.
