MOAE — Autonomous Multi-Agent Orchestration Engine
Complete Frontend Build Prompt v3.0
For AI Builder / Senior Frontend Engineer

0. WHAT YOU ARE BUILDING
A production-grade, fully functional frontend for MOAE — a developer intelligence platform where AI agents autonomously orchestrate software workflows across GitHub, Jira, and Slack. The product is aimed at developers and engineering teams who want to automate their entire dev workflow — PR creation, ticket updates, team notifications — with a single natural language command.
This is not a typical AI tool UI. The aesthetic benchmark is: Linear's clarity × Vercel's developer elegance × a Tokyo subway information display. The feeling when a user opens this app should be: I am in control of something powerful.
Tech Stack (non-negotiable):

React 18 + TypeScript
React Router v6
Framer Motion (all animations)
Tailwind CSS (utility-only, no component libraries)
Zustand (global state)
Axios (API layer)
date-fns (timestamps)
react-hot-toast (transient toasts)
JetBrains Mono + Geist (fonts — import from Google Fonts)
Zero external UI component libraries — every component is custom-built

Screens to implement (10 total):

Landing Page (/)
Sign Up Page (/signup)
Login Page (/login)
Integrations Setup Page (/integrations)
Workflow Command Center — Phase 1: Input (/workflow)
Workflow Command Center — Phase 2: Execution (same route, state-driven)
Workflow Command Center — Phase 3: Completion (same route, state-driven)
Profile Page (/profile)
Previous Workflows Page (/workflows)
Error / 404 Page (/404)


1. DESIGN PHILOSOPHY
Three intersecting philosophies define every visual decision:
Japanese Ma (間) — Intentional emptiness. Negative space is not absence; it is presence. The void is the design. Never fill space for the sake of filling it.
Glassmorphism — Layered translucency. Depth is created through stacked translucent surfaces, not color. Every card floats. Every panel breathes.
Cyberpunk Minimalism — Precision aesthetics. Monospace data readouts. Neon accent traces. Near-void backgrounds. The interface is a mission control center, not a marketing page.
What this means in practice:

Dark background so dark it's almost invisible (#080808)
Accent colors used sparingly — 5% of any screen maximum
Animations are purposeful, never decorative
Every element earns its place. If it doesn't serve the user, it doesn't exist.
Typography does heavy lifting — weight, size, and letter-spacing convey hierarchy
The three service colors (GitHub green, Jira blue, Slack purple) are the only real color in the entire product


2. DESIGN TOKENS
2.1 Color System
css:root {
  /* Backgrounds — layered from void to elevated */
  --bg-void:     #080808;   /* Page base — near-void black */
  --bg-surface:  #0F0F0F;   /* Secondary panels */
  --bg-elevated: #161616;   /* Cards, modals */
  --bg-overlay:  #1C1C1C;   /* Tooltips, popovers */

  /* Borders */
  --border-default: rgba(255, 255, 255, 0.06);
  --border-hover:   rgba(255, 255, 255, 0.12);
  --border-focus:   rgba(255, 255, 255, 0.22);
  --border-subtle:  rgba(255, 255, 255, 0.04);

  /* Text */
  --text-primary:   #F0F0F0;
  --text-secondary: #888888;
  --text-muted:     #444444;
  --text-disabled:  #2A2A2A;

  /* GitHub / Success — Green */
  --green:       #22C55E;
  --green-glow:  rgba(34, 197, 94, 0.15);
  --green-dim:   rgba(34, 197, 94, 0.08);

  /* Jira / Info — Blue */
  --blue:        #2D8EFF;
  --blue-glow:   rgba(45, 142, 255, 0.15);
  --blue-dim:    rgba(45, 142, 255, 0.08);

  /* Slack / Purple */
  --purple:      #7C3AED;
  --purple-glow: rgba(124, 58, 237, 0.15);
  --purple-dim:  rgba(124, 58, 237, 0.08);

  /* Semantic */
  --warning:   #F59E0B;
  --error:     #EF4444;
  --error-dim: rgba(239, 68, 68, 0.08);

  /* Glass recipe — applied as classes */
  --glass-bg:     rgba(255, 255, 255, 0.03);
  --glass-border: rgba(255, 255, 255, 0.07);
}
2.2 Typography
css@import url('https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap');

:root {
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, sans-serif;
  --font-mono: 'JetBrains Mono', 'Fira Code', monospace;

  /* Scale */
  --text-display: clamp(64px, 10vw, 96px);
  --text-h1:      clamp(36px, 5vw, 48px);
  --text-h2:      clamp(24px, 3.5vw, 32px);
  --text-h3:      20px;
  --text-body:    16px;
  --text-small:   14px;
  --text-caption: 12px;
  --text-micro:   11px;

  /* Letter spacing */
  --tracking-tight:   -0.04em;
  --tracking-snug:    -0.02em;
  --tracking-wide:    0.06em;
  --tracking-wider:   0.10em;
  --tracking-widest:  0.20em;
}
2.3 Glass Card Recipes
css/* Base glass — default for all cards */
.glass-card {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 16px;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.04) inset,
    0 32px 64px rgba(0, 0, 0, 0.4),
    0 8px 16px rgba(0, 0, 0, 0.2);
}

/* Service-colored variants — applied after successful connection */
.glass-card--blue {
  border-color: rgba(45, 142, 255, 0.2);
  box-shadow:
    0 0 0 1px rgba(45, 142, 255, 0.08) inset,
    0 0 0 1px rgba(45, 142, 255, 0.4),
    0 0 24px rgba(45, 142, 255, 0.12),
    0 0 48px rgba(45, 142, 255, 0.06),
    0 32px 64px rgba(0, 0, 0, 0.4);
}

.glass-card--purple {
  border-color: rgba(124, 58, 237, 0.2);
  box-shadow:
    0 0 0 1px rgba(124, 58, 237, 0.08) inset,
    0 0 0 1px rgba(124, 58, 237, 0.4),
    0 0 24px rgba(124, 58, 237, 0.12),
    0 0 48px rgba(124, 58, 237, 0.06),
    0 32px 64px rgba(0, 0, 0, 0.4);
}

.glass-card--green {
  border-color: rgba(34, 197, 94, 0.2);
  box-shadow:
    0 0 0 1px rgba(34, 197, 94, 0.08) inset,
    0 0 0 1px rgba(34, 197, 94, 0.4),
    0 0 24px rgba(34, 197, 94, 0.12),
    0 0 48px rgba(34, 197, 94, 0.06),
    0 32px 64px rgba(0, 0, 0, 0.4);
}

.glass-card--error {
  border-color: rgba(239, 68, 68, 0.2);
  box-shadow:
    0 0 0 1px rgba(239, 68, 68, 0.08) inset,
    0 0 0 1px rgba(239, 68, 68, 0.4),
    0 0 24px rgba(239, 68, 68, 0.12),
    0 32px 64px rgba(0, 0, 0, 0.4);
}
2.4 Motion System
ts// lib/animations.ts — import everywhere, never define inline
export const EASE_OUT_EXPO = [0.16, 1, 0.3, 1] as const;

export const fadeUp = {
  hidden: { opacity: 0, y: 8 },
  show:   { opacity: 1, y: 0, transition: { duration: 0.3, ease: EASE_OUT_EXPO } }
};

export const fadeUpLarge = {
  hidden: { opacity: 0, y: 20 },
  show:   { opacity: 1, y: 0, transition: { duration: 0.4, ease: EASE_OUT_EXPO } }
};

export const staggerContainer = (stagger = 0.08) => ({
  hidden: {},
  show:   { transition: { staggerChildren: stagger } }
});

export const scaleIn = {
  hidden: { opacity: 0, scale: 0.95 },
  show:   { opacity: 1, scale: 1, transition: { duration: 0.25, ease: EASE_OUT_EXPO } }
};

export const slideInFromRight = {
  hidden: { opacity: 0, x: 20 },
  show:   { opacity: 1, x: 0, transition: { duration: 0.3, ease: EASE_OUT_EXPO } }
};

// Used on every page's root motion.div
export const pageTransition = {
  initial:    { opacity: 0, x: 20 },
  animate:    { opacity: 1, x: 0 },
  exit:       { opacity: 0, x: -20 },
  transition: { duration: 0.3, ease: EASE_OUT_EXPO }
};

// Timing reference:
// Micro-interaction:   150ms
// Standard UI:         300ms
// Page transition:     300ms
// Score bar fill:      800ms
// Particle:            continuous rAF

3. SHARED COMPONENTS
3.1 Particle Background
Canvas-based, runs in requestAnimationFrame. Mount after component mounts (useEffect), destroy loop on unmount. Use useRef for canvas element and rAF ID — never let this trigger React re-renders.
Spec:

Desktop (≥768px): ~40 nodes. Mobile: ~20 nodes.
Each node: { x, y, vx, vy, opacity }
Velocity: ±0.3 px/frame (desktop), ±0.15 px/frame (login page — calmer)
Node opacity: random 0.12–0.30
Node size: 1.5px radius, white fill
Connection lines: drawn when distance < 120px (desktop), < 80px (mobile)

Line opacity: (1 - dist/120) * 0.15
Line color: rgba(255,255,255, opacity)
Line width: 0.5px


Nodes wrap at canvas edges (toroidal — if x > width, x = 0)
Canvas: full viewport, pointer-events: none, z-index: 0
Use offscreen canvas if OffscreenCanvas is supported

3.2 Navbar
Sticky, height 56px desktop / 48px mobile.
Background: rgba(8,8,8,0.75)
Backdrop: blur(20px) saturate(150%)
Bottom border: 1px solid rgba(255,255,255,0.05)
z-index: 50
Left: [LogoSymbol SVG 20px] [8px] [MOAE wordmark, Geist 700, white, -0.04em tracking] [8px] [· separator, #333] [8px] [Beta pill]
Beta pill: "Beta", 11px Geist 500, color #666, background rgba(255,255,255,0.05), border 1px solid rgba(255,255,255,0.08), border-radius 999px, padding 2px 8px.
Right (unauthenticated): [Sign Up — ghost button] [Log In — ghost button]

Ghost button: padding 8px 16px, border-radius 8px, border 1px solid rgba(255,255,255,0.12), text #888, 14px Geist 500
Hover: border rgba(255,255,255,0.25), text #F0F0F0, box-shadow 0 0 12px rgba(255,255,255,0.04)
Transition: all 150ms ease-out

Right (authenticated): [Avatar 32px circle] — GitHub avatar with 8px green online dot (border 2px solid #080808, positioned bottom-right). Click → ProfileDropdown.
Mobile ≤768px: Wordmark only, no symbol, no beta pill. Auth state identical.
3.3 Logomark Symbol (SVG)
A stylized octagon of 8 small squares in a ring with a central void — representing agents orbiting an empty core (Ma philosophy). Used in navbar, login card, and 404 page.
svg<svg width="24" height="24" viewBox="0 0 24 24" fill="none">
  <rect x="9" y="1" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.9"/>
  <rect x="17" y="4" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(45 20 7)"/>
  <rect x="18" y="9" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.6" transform="rotate(90 21 12)"/>
  <rect x="15" y="17" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(135 18 20)"/>
  <rect x="9" y="18" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.9"/>
  <rect x="2" y="17" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(-135 5 20)"/>
  <rect x="1" y="9" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.6" transform="rotate(-90 4 12)"/>
  <rect x="3" y="4" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(-45 6 7)"/>
</svg>
3.4 Form Input Component
States: default, focused, filled, error, disabled.
Structure:
  [Label: 14px #888 Geist 400] [Optional tooltip ?]
  [Input field: 44px height, border-radius 10px]
  [Helper: 12px — error red #EF4444 or hint #555]

Input field:
  background: rgba(255,255,255,0.03)
  border: 1px solid rgba(255,255,255,0.08)
  padding: 0 12px, color: #F0F0F0, font-size: 14px
  font-family: Geist (text) or JetBrains Mono (token inputs)
  placeholder: #444

  Focus:
    border: 1px solid rgba(255,255,255,0.22)
    box-shadow: 0 0 0 3px rgba(255,255,255,0.04)

  Error:
    border: 1px solid rgba(239,68,68,0.5)
    box-shadow: 0 0 0 3px rgba(239,68,68,0.08)

Password input: eye toggle icon, absolute right 12px, 16px icon, color #444 → #888 on hover.
3.5 Button System
Primary:
  background: rgba(255,255,255,0.08)
  border: 1px solid rgba(255,255,255,0.18)
  color: #F0F0F0, 15px Geist 500
  padding: 12px 24px, border-radius: 10px, height: 48px
  hover: background rgba(255,255,255,0.13), translateY(-1px),
         box-shadow: 0 0 20px rgba(255,255,255,0.06)
  active: translateY(0), 80ms
  transition: all 150ms cubic-bezier(0.16, 1, 0.3, 1)

Loading state (all variants):
  Rotating arc spinner 18px, stroke 2px
  Text → loading text provided
  pointer-events: none, opacity: 0.8
  aria-busy="true"

Ghost:
  background: transparent
  border: 1px solid rgba(255,255,255,0.08)
  color: #888
  hover: color #F0F0F0, border rgba(255,255,255,0.18)

Text-only:
  background: transparent, border: none
  color: #666, underline on hover

Service variants (extend primary):
  Blue:   border rgba(45,142,255,0.3),   hover glow rgba(45,142,255,0.15)
  Purple: border rgba(124,58,237,0.3),   hover glow rgba(124,58,237,0.15)
  Green:  border rgba(34,197,94,0.3),    hover glow rgba(34,197,94,0.15)

Danger:
  color: #EF4444, border: rgba(239,68,68,0.25)
  hover: border rgba(239,68,68,0.5), glow rgba(239,68,68,0.1)

Modifiers:
  full-width:   width: 100%
  icon-left:    icon + 8px gap before text
  icon-right:   text + 8px gap + icon
3.6 Status Chip
connected-green:
  background: rgba(34,197,94,0.08), border: 1px solid rgba(34,197,94,0.2)
  [pulsing dot 6px #22C55E] ["Connected" #22C55E]

connected-blue:
  background: rgba(45,142,255,0.08), border: 1px solid rgba(45,142,255,0.2)
  [pulsing dot 6px #2D8EFF] ["Connected" #2D8EFF]

connected-purple:
  background: rgba(124,58,237,0.08), border: 1px solid rgba(124,58,237,0.2)
  [pulsing dot 6px #7C3AED] ["Connected" #7C3AED]

disconnected:
  background: rgba(255,255,255,0.04), border: 1px solid rgba(255,255,255,0.08)
  [hollow dot 6px #444] ["Not connected" #555]

error:
  background: rgba(239,68,68,0.08), border: 1px solid rgba(239,68,68,0.2)
  [✕ icon 10px #EF4444] ["Failed" #EF4444]

Dot pulse animation:
  @keyframes dotPulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(accent, 0.4); }
    50%       { box-shadow: 0 0 0 4px rgba(accent, 0); }
  }
  animation: dotPulse 2s ease infinite
3.7 Score Bar
Props: value (0–100), label, showValue
Structure:
  [Label 13px #888] ............. [Value 13px JetBrains Mono, color-matched]
  [Track: full width, 4px height, rgba(255,255,255,0.06), border-radius 2px]
  [Fill: 0 → value%, animated 800ms EASE_OUT_EXPO]

Auto-color by value:
  90–100: #22C55E (green)
  70–89:  #F59E0B (amber)
  0–69:   #EF4444 (red)

Fill gradient: linear-gradient(to right, color, color at 0.7 opacity)
Trigger: IntersectionObserver — animate only on first viewport entry.
3.8 Profile Dropdown
Position: fixed, top 64px, right 16px, width 220px
Animation: scale 0.95→1, opacity 0→1, y -8px→0, 200ms EASE_OUT_EXPO
transform-origin: top right

Layout:
  [User section — padding 12px 16px]:
    [Avatar 40px] [Name 14px Geist 600 #F0F0F0 / @username 12px mono #555]
  [Divider]
  [Menu items — 36px height each]:
    ⊙ Profile         → /profile
    ⌚ Previous Workflows → /workflows
    ⚙ Settings        → /settings
  [Divider]
  [Log Out — #EF4444 on hover]

Item hover: background rgba(255,255,255,0.04), 100ms transition
Close on: outside click, Escape key, navigation

4. PAGE SPECIFICATIONS

PAGE 1 — Landing Page (/)
Navbar
Unauthenticated. Right side: [Sign Up — ghost] [Log In — ghost]. Sign Up → /signup. Log In → /login.
Hero Section
Container: 100dvh, flexbox column, centered on both axes.
Background:
cssbackground: #080808;
background-image: radial-gradient(
  ellipse 60% 40% at 50% 60%,
  rgba(45, 142, 255, 0.04) 0%,
  transparent 70%
);
Particle background: active (§3.1). z-index: 0. Content: z-index: 1, max-width 720px.
Animation sequence (Framer Motion staggered, all entering from y: 8, opacity: 0):
1. Eyebrow pill (delay 0ms):

Text: AUTONOMOUS · AI · ORCHESTRATION — JetBrains Mono, 11px, letter-spacing 0.15em
Style: glass pill — background: rgba(45,142,255,0.06), border: 1px solid rgba(45,142,255,0.2), padding 6px 14px, border-radius 999px
Text color: rgba(45,142,255,0.8)

2. Main title (delay 100ms, letter-by-letter animation):

Text: MOAE
Size: clamp(72px, 12vw, 96px), Geist 800, white, letter-spacing -0.04em
Each letter: opacity 0→1, y: 16px→0, 300ms, staggered 60ms per letter

3. Subtitle (delay 350ms):

Autonomous Multi-Agent Orchestration Engine
18px Geist 400, color #666, letter-spacing 0.04em

4. Description (delay 500ms):

Automate developer workflows across GitHub, Jira, and Slack using intelligent AI agents. One command. Complete execution.
16px, #555, max-width 440px, line-height 1.7, centered

5. Feature chips row (delay 620ms):

Three glass pills: [⬡ GitHub Automation] [◆ Jira Sync] [# Slack Alerts]
Each: 12px mono, border in respective service color at 0.2 opacity, background at 0.04 opacity
Staggered 60ms entrance

6. CTA Group (delay 750ms):

→ Start New Workflow — primary button → /login
Sign Up Free — text-only → /signup
Layout: row, gap 12px, centered. On mobile: column, full width.

7. Scroll indicator (delay 1000ms):

1px wide, 32px tall gradient line below CTAs
Gradient: rgba(255,255,255,0.3) → transparent
Continuous: opacity 0.8→0.2→0.8, translateY 0→6px→0, 2s ease-in-out infinite

How MOAE works — brief strip (below hero fold):
Three columns separated by thin dividers, max-width 720px, padding 64px 0:
[01]                    [02]                    [03]
Describe it             Agents execute           Ship it
Type what you want      GitHub, Jira, Slack      Review the score
in plain English.       handled in seconds.      and iterate.
Text: label 11px mono #444 tracking 0.15em, heading 20px Geist 600 #F0F0F0, body 14px #555.
Entrance: fadeUp staggered 100ms on scroll into view.
Mobile:

Title: clamp(48px, 12vw, 64px)
Feature chips: wrap
CTA: column, full-width
Particles: 20 nodes
Bloom: opacity 0.03


PAGE 2 — Sign Up Page (/signup)
Layout: Full viewport. No navbar. MOAE logomark centered at top (36px from top), click → /. Particle background (25 nodes, slow velocity).
Sign Up Card:

Width: 480px, max-width 90vw
Position: absolute center
glass-card with border-top: 1px solid rgba(45,142,255,0.3)
Animation: scaleIn, delay 100ms

Card interior (padding 40px):
[Logomark symbol 24px, #888, centered]
[16px]
[Divider]
[24px]
["Create your account" — 28px Geist 600 #F0F0F0, centered]
["Join MOAE and automate your dev workflow." — 14px #555, centered]
[32px]

[GitHub OAuth Button — full width, 48px]
  [GitHub Octocat SVG 18px white] ["Continue with GitHub"] 
  On click: spinner + "Creating account…"
  On success → /integrations

[32px]
[Divider with "or sign up with email" — 12px #333]
[24px]

[Full Name input — placeholder "Your full name"]
[12px]
[GitHub Username input — placeholder "your-github-handle", JetBrains Mono, prepended "@"]
[12px]
[Email input — placeholder "you@company.com"]
[12px]
[Password input — placeholder "Choose a strong password", eye toggle]
[24px]

[Create Account button — primary, full width]
[20px]
["Already have an account?" [Log in] link — 14px #555]
[20px]
["By continuing, you agree to our Terms & Privacy." — 12px #333, centered]
GitHub Username input special treatment:

Left padding accounts for @ prefix rendered inside the field
@ symbol: absolute positioned, 14px mono, #555, left 12px
Input left padding: 24px

Validation:

Full name: required, min 2 chars
GitHub username: no spaces, only alphanumeric and hyphens
Email: valid format
Password: min 8 chars — strength indicator bar below input (weak red → medium amber → strong green), 3px height, animates on type

Error handling:

Inline errors below each field
"Email already exists" — red chip below Create Account button, fadeUp, auto-dismiss 5s


PAGE 3 — Login Page (/login)
Layout: Full viewport. No navbar. Logomark top-center, click → /. Particle background (25 nodes, slow).
Login Card:

Width: 480px, max-width 90vw
glass-card, border-top: 1px solid rgba(45,142,255,0.3)
Animation: scaleIn, delay 100ms

Card interior (padding 40px):
[Logomark symbol 24px, #888, centered]
[16px]
[Divider]
[24px]
["Welcome back" — 28px Geist 600 #F0F0F0, centered]
["Sign in to your account" — 14px #555, centered]
[32px]

[GitHub OAuth Button — full width, 48px]
  On click: spinner + "Authenticating…"
  On success: → /integrations (new user) or /workflow (returning user with integrations)

[32px]
[Divider with "or continue with email"]
[24px]

[Email input]
[16px]
[Password input with eye toggle]
[8px]
["Forgot password?" — text link, 12px #555, right-aligned]
[24px]

[Sign In button — primary, full width]
[20px]
["Don't have an account?" [Sign up] link — 14px #555]
[20px]
["By continuing, you agree to our Terms & Privacy." — 12px #333]
Error state: Red chip below Sign In button: "Invalid credentials. Please try again." — fadeUp, auto-dismiss 5s.
Mobile: Card full-width, 24px horizontal margin, padding 28px.

PAGE 4 — Integrations Setup Page (/integrations)
Layout: Centered column, max-width 720px, min-height 100dvh, padding 64px 24px. Plain #080808 background — no particles (too much noise with the form cards). Very subtle SVG noise texture overlay at opacity 0.015.
Page Header
[Back: "← Setup" — 13px #555, hover #888]
[16px]
["Connect your tools" — var(--text-h1), Geist 700, letter-spacing -0.03em]
[12px]
["MOAE needs access to your services to automate workflows." — 16px #666]
[24px]
[Progress pill: glass chip, "Step 2 of 3" — 12px mono #555]
[32px]
[Horizontal rule: 1px rgba(255,255,255,0.06)]
[32px]
GitHub Connected Banner
User just authenticated via GitHub — show confirmation:
Glass chip inline-flex, gap 10px, padding 10px 16px, border-radius 10px
[GitHub Octocat icon 16px — #22C55E]
["github.com/{username}" — 13px JetBrains Mono #888]
[StatusChip: connected-green]
Entrance: fadeUp, delay 50ms.
Jira Integration Card
Full-width glass card, margin-bottom 24px. Entrance: fadeUp, delay 100ms.
Card header (padding 24px 24px 20px):
Row: [Jira diamond SVG 24px, #2D8EFF] [10px] ["Jira" 17px Geist 600 / "Atlassian" 13px #555] + Right: [StatusChip — disconnected initially]
[Divider: 1px rgba(255,255,255,0.06)]
Card body (padding 20px 24px 24px):
Fields:

Jira Domain — label + tooltip ("Your Atlassian Cloud URL, e.g. yourcompany.atlassian.net"). Placeholder: yourcompany.atlassian.net. Validation: must contain .atlassian.net.
Account Email — type email, placeholder you@company.com.
API Token — type password, JetBrains Mono, eye toggle, placeholder ••••••••••••••••. Below: "Generate API token →" link → https://id.atlassian.com/manage-profile/security/api-tokens, opens new tab, style: 12px #2D8EFF, underline on hover.

Connect Jira button: Blue variant, full width, text "Connect Jira".
Loading: spinner + "Verifying connection…"
On success:

Button → solid #2D8EFF, text "✓ Connected to Jira", pointer-events none
Card class transitions to .glass-card--blue (CSS transition on box-shadow, 600ms)
StatusChip → connected-blue
Single neon ripple animation from card border outward (keyframe: scale 1→1.01, opacity 0.4→0, 600ms)
Detail row appears fadeUp: "Connected as {displayName} · {accountId}" — 12px #555 mono

Error states:

Invalid domain → inline error below domain input
401 from API → red chip below button: "❌ Authentication failed. Check your email and API token."
Network error → "⚡ Connection timed out. Check your domain and try again."
Rate limited → "⏳ Too many attempts. Wait 30 seconds."

API call spec (for backend):
GET https://{domain}/rest/api/3/myself
Authorization: Basic base64(email:apiToken)
Accept: application/json
200 response: { accountId, displayName, emailAddress }
Store encrypted: { domain, email, encryptedToken, accountId, displayName }
Slack Integration Card
Same glass card structure, below Jira. Gap: 24px. Entrance: fadeUp, delay 200ms.
Card header: [Slack hash SVG 24px, #7C3AED] ["Slack" / "Workspace Bot"] [StatusChip: disconnected]
Card body:
Fields:

Bot Token — label + tooltip ("From your Slack App → OAuth & Permissions → Bot User OAuth Token. Starts with xoxb-"). Input: password type, JetBrains Mono, eye toggle, placeholder xoxb-••••-••••-••••-••••••••••••. Validation: must start with xoxb-.
Below input links (stacked, 8px apart):

"Create a Slack App →" → https://api.slack.com/apps
"View required permissions →" → opens modal listing: chat:write, channels:read, channels:join



Connect Slack button: Purple variant, text "Connect Slack".
On success:

Button → solid #7C3AED, "✓ Connected to Slack", pointer-events none
Card → .glass-card--purple transition
StatusChip → connected-purple
Detail row: "Connected to {workspace_name} · Bot: {bot_name}" — 12px #555 mono

Error states:

Invalid token: "Token must begin with xoxb-"
Auth fail: "❌ Bot token rejected. Regenerate from your Slack App dashboard."
Missing scopes: "⚠ Bot missing required scopes: chat:write, channels:read."

API call spec:
GET https://slack.com/api/auth.test
Authorization: Bearer {botToken}
Expected: { ok: true, team, team_id, user, bot_id }
If ok: false → show error.error
Store: { botToken encrypted, team, teamId, botId, botName }
Proceed Button
Always in DOM, hidden. Becomes visible + animates in when both Jira and Slack show connected.
Animation: translateY(20px)→0, opacity 0→1, 400ms EASE_OUT_EXPO

Button: "→ Launch Command Center"
  Width: 100%, height 56px, border-radius 12px
  background: rgba(255,255,255,0.06)
  border: 1px solid rgba(255,255,255,0.15)
  
On mount-visible: single shimmer sweep left→right across button, 600ms, once only.

Hover:
  Border color cycles: blue → purple → green → blue
  CSS animation or JS-driven hue-rotate, 3s cycle, continuous while hovered
  Glow shifts to match current border color

Click → /workflow

Mobile: Button fixed at bottom, full-width, safe-area-inset padding.

PAGES 5–7 — Workflow Command Center (/workflow)
Single route, three visual phases driven by useState<'input' | 'executing' | 'complete'>.
Top Bar (persistent across all phases)
Sticky, top: 0, z-index: 40, height: 56px
background: rgba(8,8,8,0.85), backdrop-filter: blur(16px)
border-bottom: 1px solid rgba(255,255,255,0.05)

Left: [MOAE wordmark] [/] ["Command Center" — 14px #555]
Right: [Avatar 32px] with green online dot

PAGE 5 — Phase 1: Input Mode
Content: Centered vertically and horizontally below top bar.
Title: "What would you like to automate?" — 28px Geist 600 #F0F0F0, centered, margin-bottom 32px.
Command Textarea:
glass-card style — background rgba(255,255,255,0.02), border 1px solid rgba(255,255,255,0.08)
border-radius: 16px, padding: 20px
width: min(720px, 90vw), min-height: 160px, resize: none
font-size: 16px, color: #F0F0F0, font-family: Geist, line-height: 1.7

Focus:
  border: 1px solid rgba(255,255,255,0.2)
  box-shadow: 0 0 0 3px rgba(255,255,255,0.03)
Placeholder typewriter (rotate with 40ms/char, 2s pause, 400ms fade-out between):

"Create a PR for the auth feature, update Jira JIR-420, and notify #dev-team on Slack..."
"Fix the login bug, push to staging branch, move ticket to In Review, alert the team..."
"Review open PRs older than 3 days, comment with AI summary, ping #engineering..."
"Close all resolved Jira tickets, archive Slack threads, tag release v2.1.0..."

Hide when textarea is focused or has content.
Textarea toolbar (inside card, bottom row, border-top 1px rgba(255,255,255,0.06), padding 12px 0 0):

Left: icon chips (28px height, glass style, gap 6px):

[GitHub icon + branch dropdown]
[Jira icon + project dropdown]
[Slack icon + channel dropdown]


Right: {n} / 500 — JetBrains Mono 12px #444

Run Workflow button (below textarea, 16px gap):

Primary, height 52px, min-width 200px
Text: "▶  Run Workflow" with keyboard hint "⌘↵" in JetBrains Mono 12px #333
Disabled with tooltip "Enter a command first" when textarea empty
On click with content → transition to Phase 2

Suggestions strip (below button, 12px gap):
"Try:" [14px #444] then chips:
["Update ticket JIR-123"] ["Create branch for feature-x"] ["Notify #releases"] ["Draft PR description"]

Chip: glass, border rgba(255,255,255,0.07), padding 6px 12px, border-radius 999px
      13px #555, hover: border rgba(255,255,255,0.12) color #888
Clicking populates textarea and focuses it.

PAGE 6 — Phase 2: Execution Mode
Transition: Textarea shrinks from 160px → 56px height, 400ms. Content fades to compact read-only command strip.
Command strip:
Glass chip, full width, max-width 720px, height 56px, padding 0 16px
[⚡ bolt icon 16px, #F59E0B] [12px] [submitted command — 14px mono #888, ellipsis]
Right: [× reset button — only in non-critical steps]
Execution Timeline (below command strip, 24px gap):
Vertical stack, max-width 720px, centered. Left edge: thin track line (2px) filled with gradient as steps complete.
Each step card:
glass-card, margin-bottom 12px, padding 16px 20px
border-left: 3px solid [service accent color]
Entrance: fadeUp, 80ms stagger

Row: [Step badge 32px] [Content column flex:1, margin 0 16px] [Status indicator]

Step badge:
  Pending:  border 2px solid #222, text #444
  Active:   border 2px solid service-color, text service-color, slight glow
  Complete: filled service-color, white checkmark

Content:
  Name: 15px Geist 600 #F0F0F0
  Sub-description: 13px #555 pending → #888 active

Status indicator:
  Pending: nothing
  Active:  rotating arc spinner 24px, stroke 2px, service color
  Complete: checkmark, scale 0→1.2→1 spring, 300ms
  Error:   ✕ red

Completion detail (fadeUp after complete, padding-left 48px):
  Link: 13px JetBrains Mono, service color, hover underline
Timeline connecting line:
position: absolute, left: 31px, top: 0, bottom: 0, width: 2px
Track: #1C1C1C
Fill: linear-gradient(to bottom, #22C55E, #2D8EFF, #7C3AED)
Fill animates via scaleY transform + transform-origin top, as steps complete
Step definitions:
Step 1 — GitHub (green):
  "Initializing Connection"
  "Connecting to github.com/{username}/{repo}"
  Complete: "Repository accessed · {repo_name} ↗"

Step 2 — GitHub (green):
  "Branch Operations"
  "Creating branch: feature/{ai-generated-slug}"
  Complete: "✓ Branch created → feature/auth-login ↗"

Step 3 — GitHub (green):
  "Pull Request"
  "Generating AI-written PR description"
  Complete: "✓ PR #{number} opened → '{AI title}' ↗"

Step 4 — Jira (blue):
  "Ticket Update"
  "Updating ticket status and linking PR"
  Complete: "✓ JIR-{id} → 'In Review', PR linked ↗"

Step 5 — Slack (purple):
  "Team Notification"
  "Composing and sending message to #{channel}"
  Complete: "✓ Message sent to #{channel} ↗"
Step timing (demo/fallback): Sequential, random delay 1200ms–2800ms per step, each "active" for 800ms–2000ms.
Error handling within steps:

Failed step: badge shows ✕ red, card border → .glass-card--error
Error detail row: "❌ {Error}: {message}" in red
Below error: [Retry this step] button + [Skip and continue] button (if applicable)
Subsequent steps: dimmed, "Waiting…" sub-text
Global banner at timeline top: "Workflow paused at Step {n}"


PAGE 7 — Phase 3: Completion Screen
All 5 steps complete → timeline fades to 60% opacity → completion card animates up below.
Completion Card:
glass-card--green, max-width 720px, width 100%, padding 32px
Entrance: fadeUpLarge, 400ms delay after last step completes

[Row: [✓ circle 24px green] [16px] ["Workflow Completed" 22px Geist 600 #F0F0F0]]
[12px] [Divider] [24px]

[Large Score — centered]:
  [94, 64px Geist 800 #F0F0F0] ["/100", 24px #444]
  [12px]
  ["DECISION QUALITY SCORE" — 12px #555, letter-spacing 0.08em]
  [20px]
  [ScoreBar full width, 6px height, value=94]

[32px]
[Score breakdown — 2×2 desktop, 1×4 mobile]:
  Task Completion: 87/100
  Decision Accuracy: 91/100
  Execution Efficiency: 89/100
  Context Relevance: 96/100
[24px]

[Rating badge: "Overall: Excellent" — glass chip, green-tinted, centered]
[16px]
[AI summary: 14px #666, line-height 1.7, max-width 560px, centered]
[32px]

[Button row: centered, gap 12px]:
  [↺ Run Another Workflow — ghost, resets to Phase 1]
  [View Full Report → opens execution log drawer]
Rating thresholds:

90–100: "Excellent" — green
70–89: "Good" — amber
0–69: "Needs Improvement" — red

Execution Log Drawer (right slide-in):
Width: min(600px, 90vw), height: 100vh
Slides from right: x 600px→0, 350ms EASE_OUT_EXPO
Overlay: rgba(0,0,0,0.6) left side

Header (sticky, 64px):
  [←] ["Execution Report" 17px Geist 600] [✕]

Scrollable:
  1. Submitted command — full text, glass chip
  2. Overall score + score bars
  3. "Execution Log" section
     Each step as accordion:
       Header: [badge] [name] [status chip] [time "1.2s"]
       Expanded:
         AI reasoning text — 14px #666 line-height 1.7
         Raw API request — collapsible JSON block
         Raw API response — collapsible JSON block
         JSON: JetBrains Mono 12px, keys #2D8EFF, strings #22C55E, numbers #F59E0B
  4. Actions:
     [Re-run — primary] [Export as JSON — ghost]

PAGE 8 — Profile Page (/profile)
Layout: Centered column, max-width 680px, padding 64px 24px.
Back link: "← Command Center" → /workflow
Profile Header Card (glass-card, fadeUp entrance)
padding: 32px, text centered

[GitHub avatar 72px, border 3px solid rgba(255,255,255,0.1)]
[16px]
[Name — 24px Geist 600 #F0F0F0]
[Company — 16px #666]
[Email — 13px JetBrains Mono #555]
[20px]
[Service chips row, centered, gap 10px]:
  [⬡ GitHub · @{username}] — green-bordered pill
  [◆ Jira · {accountId}] — blue-bordered pill
  [# Slack · {workspace}] — purple-bordered pill
Each: 12px mono, padding 4px 10px, border-radius 999px
Credentials Card (glass-card, margin-top 16px)
padding: 24px
Header: "CONNECTED CREDENTIALS" — 11px Geist 600 #444, letter-spacing 0.12em

[Row each 48px, border-bottom rgba(255,255,255,0.04)]:
  [Service icon 16px] [Label 14px #F0F0F0, flex 0 0 120px]
  [Masked token JetBrains Mono 13px #555] [flex: 1] [Copy icon 20px #444→#888]

GitHub Token    — first 4 + masked + last 4
Jira API Token  — fully masked + last 4
Slack Bot Token — xoxb- + masked + last 4

Copy behavior:
  Full unmasked token → clipboard
  Icon → ✓ checkmark for 1200ms
  Tooltip "Copied!" above icon 1200ms
  Then reverts
Account Stats Card (glass-card, margin-top 16px)
padding: 24px
Header: "ACCOUNT OVERVIEW"

3-column stat grid:
  Total Workflows     Avg Quality Score    Success Rate
       {n}                {n}/100              {n}%
  28px Geist 700    28px Geist 700        28px Geist 700
  12px #555 label   12px #555 label       12px #555 label

Column dividers: 1px rgba(255,255,255,0.06)
Danger Zone Card (glass-card--error, margin-top 24px)
padding: 20px 24px
Header: "DANGER ZONE" — 12px #EF4444, uppercase

Row: ["Disconnect all integrations" 14px #888] [Disconnect button — danger, small]
Row: ["Delete account" 14px #888] [Delete button — danger, small]
Both require confirmation modal (glass overlay, "Are you sure?" with Cancel + Confirm danger button)

PAGE 9 — Previous Workflows Page (/workflows)
Layout: Max-width 900px, centered, padding 64px 24px.
Page Header
[Row: ["Workflow History" — var(--text-h1) Geist 700] ["{n} workflows" glass chip, 13px mono]]
[12px]
[Filter tabs: All | Success | Failed]
  Active: background rgba(255,255,255,0.08), border rgba(255,255,255,0.12), color #F0F0F0
  Inactive: color #555, hover #888, border-radius 999px, padding 6px 14px
[24px]
[Search bar: glass input, full width, search icon left 16px, placeholder "Search workflows..."]
Empty State (zero workflows)
Centered column, margin-top 80px, gap 16px

[SVG icon: 3 abstract lines + spark, 48px, color #1C1C1C]
["No workflows yet" — 20px Geist 600 #333]
["Run your first workflow to see results here." — 14px #444]
[20px]
["→ Start a Workflow" — ghost button → /workflow]
Filtered empty (e.g., "Failed" filter with no failures):
["No failed workflows" — 20px #333]
["Everything is running smoothly." — 14px #444]
["Clear filter" — text link]
Workflow Card (glass-card)
Each card. Entrance: fadeUp, 60ms stagger.
padding: 20px 24px, margin-bottom: 12px

[Header row]:
  Left: [Status dot ● 8px] ["Success"/"Failed" 13px color-matched] [· #333] ["2h ago" 13px #555] [· #333] ["Mar 15, 2025" 13px #555]
  Right: [Score chip: "94 / 100" glass, 12px mono]

[12px]
[Command text: 15px #888, line-clamp 2, ellipsis]
[16px]

[Score bars — 3 side by side desktop, stacked mobile]:
  Task Completion    Decision Accuracy    Efficiency
  Each: 11px #555 label + 3px bar + 11px mono value

[16px]

[Footer row]:
  Left: [Step indicators — 20px circles]:
    ✓ complete: filled service-color, white checkmark 10px
    ✗ failed:   filled red, white X 10px
    — pending:  outlined #333

  Right: [→ View Details — ghost small] [↺ Re-run — ghost small]
Failed card treatment:

Border: 1px solid rgba(239,68,68,0.15)
Left border accent: 3px solid #EF4444
Error row: [⚠] ["Error at Step 3: GitHub API rate limit exceeded" 13px #EF4444]

Card hover: border rgba(255,255,255,0.1), translateY(-1px), box-shadow deepens.
Workflow Detail Drawer
Width: min(640px, 95vw), height: 100vh
Fixed: right 0, top 0
Background: #0F0F0F
Border-left: 1px solid rgba(255,255,255,0.08)
Slide-in: x 640px→0, 350ms EASE_OUT_EXPO
Overlay: rgba(0,0,0,0.5)

Header (sticky 64px, border-bottom):
  [←] ["Workflow Details" 17px Geist 600] [✕]

Scrollable:
  1. Full command — glass chip, 15px #888
  2. Overall score + all score bars
  3. "EXECUTION LOG" section label
     Each step accordion:
       Header: [badge] [name] [status chip] [time "1.2s"]
       Expanded:
         AI reasoning — 14px #666 line-height 1.7
         Raw API request (collapsible JSON)
         Raw API response (collapsible JSON)
         JSON syntax: keys #2D8EFF, strings #22C55E, numbers #F59E0B
  4. Actions:
     [Re-run — primary] [Export JSON — ghost]
Pagination: 20 cards initially. Scroll bottom → load 20 more. Skeleton cards while loading:
Same glass-card dimensions.
Shimmer: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0.03) 75%)
background-size: 200% 100%
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
animation: shimmer 1.8s ease-in-out infinite

PAGE 10 — Error / 404 Page (/404)
Full viewport, centered content, particle background (dim — node opacity 0.06–0.12)

["404" — 120px Geist 800, color #1C1C1C (watermark)]
[32px]
[Logomark symbol 32px, #333]
[24px]
["Page not found" — 24px Geist 600 #F0F0F0]
["The page you're looking for doesn't exist or has been moved." — 16px #555]
[32px]
[Button row]:
  [← Go Home — primary → /]
  [Open Command Center — ghost → /workflow]

5. LOADING STATES
Page-Level Loading Bar (route transitions)
Fixed, top 0, height 2px, z-index 9999
Background: linear-gradient(to right, #2D8EFF, #7C3AED, #22C55E)
Starts: 0% width → animates to ~80% quickly → 100% on complete → fade out
Skeleton Cards
Same dimensions as target card.
Shimmer animation — see workflow card pagination above.
Content areas: rectangles of varying widths matching actual card layout.
Avatar Loading
Circular skeleton (matching 72px / 32px dimensions), shimmer animation applied.

6. RESPONSIVE BEHAVIOR
Breakpoints
xs:  0–375px    (small mobile)
sm:  376–640px  (mobile)
md:  641–768px  (large mobile / tablet)
lg:  769–1024px (tablet / small desktop)
xl:  1025px+    (desktop — primary target)
Layout Adaptations
PageMobile treatmentLandingStack CTA column; title 52px; full-width description; 20 particlesSign Up / LoginFull-width card, 24px margin, padding 28pxIntegrationsCards full-width; 16px font on inputs (prevents iOS zoom); proceed button fixed bottomCommand CenterTextarea full-width, min-height 120px; suggestion chips wrap; score grid 1-colProfileAll cards full-width; credentials rows wrap; token on next lineWorkflow HistoryCards full-width; score bars stack; detail drawer full-width
Navbar Mobile (≤768px)
Height: 48px
Logo: wordmark only (no symbol, no beta pill)
Right: avatar or auth buttons (same as desktop)
Touch Targets
All interactive elements: minimum 44×44px tap target on mobile.

7. ACCESSIBILITY

All inputs: aria-label or aria-labelledby
Error messages: role="alert", aria-live="polite"
Modals/drawers: role="dialog", aria-modal="true", focus trapped, Escape to close
Buttons loading state: aria-busy="true"
Score bars: role="progressbar", aria-valuenow, aria-valuemin="0", aria-valuemax="100"
Color is never the sole conveyor of information — always paired with icon or text
All elements keyboard-navigable with visible focus rings:

css  :focus-visible {
    outline: 2px solid rgba(255,255,255,0.4);
    outline-offset: 2px;
    border-radius: inherit;
  }

Reduced motion:

tsx  // In animations.ts — check useReducedMotion() from framer-motion
  // If true: set all duration to 0, remove translateY/translateX transforms
  // Keep opacity transitions — they're safe for vestibular disorders

8. STATE MANAGEMENT (Zustand)
ts// store/authStore.ts
interface AuthState {
  isAuthenticated: boolean;
  user: {
    id: string;
    name: string;
    username: string;
    email: string;
    company: string;
    avatarUrl: string;
  } | null;
  token: string | null;
  setUser: (user: AuthState['user'], token: string) => void;
  logout: () => void;
}

// store/integrationStore.ts
interface IntegrationState {
  github: { connected: boolean; username: string; }
  jira:   { connected: boolean; domain: string; displayName: string; accountId: string; }
  slack:  { connected: boolean; teamName: string; botName: string; }
  setJira:  (data: IntegrationState['jira']) => void;
  setSlack: (data: IntegrationState['slack']) => void;
}

// store/workflowStore.ts
interface WorkflowState {
  phase: 'input' | 'executing' | 'complete' | 'error';
  command: string;
  steps: WorkflowStep[];
  score: WorkflowScore | null;
  history: WorkflowRecord[];
  setPhase: (phase: WorkflowState['phase']) => void;
  setCommand: (cmd: string) => void;
  updateStep: (id: string, update: Partial<WorkflowStep>) => void;
  setScore: (score: WorkflowScore) => void;
  addToHistory: (record: WorkflowRecord) => void;
  reset: () => void;
}

interface WorkflowStep {
  id: string;
  service: 'github' | 'jira' | 'slack';
  name: string;
  description: string;
  status: 'pending' | 'active' | 'complete' | 'error';
  completionDetail?: string;
  errorMessage?: string;
  timeTaken?: number;
  rawRequest?: object;
  rawResponse?: object;
  aiReasoning?: string;
}

interface WorkflowScore {
  overall: number;
  taskCompletion: number;
  decisionAccuracy: number;
  executionEfficiency: number;
  contextRelevance: number;
  summary: string;
}

9. ROUTING STRUCTURE
tsx// App.tsx
<AnimatePresence mode="wait">
  <Routes>
    <Route path="/"             element={<LandingPage />} />
    <Route path="/signup"       element={<SignUpPage />} />
    <Route path="/login"        element={<LoginPage />} />
    <Route path="/integrations" element={<ProtectedRoute><IntegrationsPage /></ProtectedRoute>} />
    <Route path="/workflow"     element={<ProtectedRoute><WorkflowPage /></ProtectedRoute>} />
    <Route path="/profile"      element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
    <Route path="/workflows"    element={<ProtectedRoute><WorkflowHistoryPage /></ProtectedRoute>} />
    <Route path="/settings"     element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
    <Route path="*"             element={<NotFoundPage />} />
  </Routes>
</AnimatePresence>

// ProtectedRoute:
//   If not authenticated → navigate to /login
//   If authenticated but no integrations connected → navigate to /integrations
//   Else → render children
//
// Page transition: every page root is a <motion.div> with pageTransition props

10. MOCK DATA (Demo Mode)
When VITE_DEMO_MODE=true, all API calls return mock data after setTimeout(800–1500ms).
ts// lib/mockData.ts

const MOCK_USER = {
  id: "usr_a4f9b2",
  name: "Rishank Gupta",
  username: "rishankgupta",
  email: "rishank@example.com",
  company: "Galgotias University",
  avatarUrl: "https://avatars.githubusercontent.com/u/583231"
};

const MOCK_INTEGRATIONS = {
  github: { connected: true, username: "rishankgupta" },
  jira:   { connected: true, domain: "rishank.atlassian.net", displayName: "Rishank Gupta", accountId: "acc_5f92b" },
  slack:  { connected: true, teamName: "Galgotias Dev", botName: "moae-bot" }
};

const MOCK_WORKFLOWS: WorkflowRecord[] = [
  {
    id: "wf_001",
    command: "Create a PR for the auth feature, update Jira JIR-420, and notify #dev-team on Slack",
    status: "success",
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
    score: {
      overall: 94,
      taskCompletion: 87,
      decisionAccuracy: 91,
      executionEfficiency: 89,
      contextRelevance: 96,
      summary: "All 5 steps executed successfully. The agent correctly identified the target branch, generated contextually accurate PR description, and routed notifications to the correct Slack channel."
    },
    steps: [
      { id: "s1", service: "github", name: "Initializing Connection", status: "complete", timeTaken: 0.9 },
      { id: "s2", service: "github", name: "Branch Operations", status: "complete", completionDetail: "feature/auth-login created", timeTaken: 1.4 },
      { id: "s3", service: "github", name: "Pull Request", status: "complete", completionDetail: "PR #47 opened", timeTaken: 2.1 },
      { id: "s4", service: "jira",   name: "Ticket Update", status: "complete", completionDetail: "JIR-420 → In Review", timeTaken: 1.2 },
      { id: "s5", service: "slack",  name: "Team Notification", status: "complete", completionDetail: "Sent to #dev-team", timeTaken: 0.7 }
    ]
  },
  {
    id: "wf_002",
    command: "Fix login bug, push to staging, move JIR-389 to In Review",
    status: "failed",
    createdAt: new Date(Date.now() - 26 * 60 * 60 * 1000),
    score: {
      overall: 61,
      taskCompletion: 100,
      decisionAccuracy: 66,
      executionEfficiency: 30,
      contextRelevance: 88,
      summary: "Partial execution — GitHub rate limit hit at Step 3."
    },
    steps: [
      { id: "s1", service: "github", name: "Initializing Connection", status: "complete", timeTaken: 0.8 },
      { id: "s2", service: "github", name: "Branch Operations", status: "complete", timeTaken: 1.2 },
      { id: "s3", service: "github", name: "Pull Request", status: "error", errorMessage: "GitHub API rate limit exceeded", timeTaken: 0 },
      { id: "s4", service: "jira",   name: "Ticket Update", status: "pending" },
      { id: "s5", service: "slack",  name: "Team Notification", status: "pending" }
    ],
    errorMessage: "GitHub API rate limit exceeded"
  }
  // Add 8 more entries mixing success and failure states
];

11. ENVIRONMENT CONFIG
bash# .env.local
VITE_API_BASE_URL=http://localhost:8080/api
VITE_GITHUB_CLIENT_ID=your_github_client_id
VITE_GITHUB_REDIRECT_URI=http://localhost:5173/auth/callback
VITE_DEMO_MODE=true

12. FILE STRUCTURE
src/
├── assets/
│   ├── fonts/                    # Local font fallbacks
│   └── icons/                    # SVG icons: GitHub, Jira, Slack, Logomark
├── components/
│   ├── common/
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── GlassCard.tsx
│   │   ├── StatusChip.tsx
│   │   ├── ScoreBar.tsx
│   │   ├── Tooltip.tsx
│   │   ├── Spinner.tsx
│   │   └── Skeleton.tsx
│   ├── layout/
│   │   ├── Navbar.tsx
│   │   ├── ProfileDropdown.tsx
│   │   └── PageWrapper.tsx       # motion.div with pageTransition
│   ├── background/
│   │   └── ParticleBackground.tsx
│   └── workflow/
│       ├── StepCard.tsx
│       ├── TimelineConnector.tsx
│       ├── CompletionCard.tsx
│       └── ExecutionDrawer.tsx
├── pages/
│   ├── LandingPage.tsx
│   ├── SignUpPage.tsx
│   ├── LoginPage.tsx
│   ├── IntegrationsPage.tsx
│   ├── WorkflowPage.tsx
│   ├── ProfilePage.tsx
│   ├── WorkflowHistoryPage.tsx
│   ├── SettingsPage.tsx
│   └── NotFoundPage.tsx
├── store/
│   ├── authStore.ts
│   ├── integrationStore.ts
│   └── workflowStore.ts
├── hooks/
│   ├── useTypewriter.ts           # Placeholder cycling typewriter
│   ├── useIntersectionObserver.ts # ScoreBar viewport trigger
│   └── useClickOutside.ts         # Dropdown close
├── lib/
│   ├── api.ts                     # Axios instance + interceptors + demo mode wrapper
│   ├── mockData.ts
│   └── animations.ts              # All Framer Motion variants — exported once, used everywhere
├── styles/
│   └── globals.css                # CSS variables, resets, glass classes, dot-pulse keyframe
├── App.tsx
└── main.tsx

13. CRITICAL IMPLEMENTATION NOTES

Start with globals.css before any component. Every visual derives from CSS variables. No hardcoded color strings in components.
Build order: globals.css → shared components (Button, Input, GlassCard, StatusChip, ScoreBar) → layout (Navbar, PageWrapper) → pages in order.
ParticleBackground is canvas-only. Initialize after mount (useEffect), destroy rAF loop on unmount. Use useRef for canvas and rAF ID. Never trigger React state updates from the animation loop — that would kill performance.
All Framer Motion variants live in lib/animations.ts. Import and use. Never define variants inline inside component JSX — it breaks memoization and creates new objects on every render.
WorkflowPage owns its own phase state via useState<'input' | 'executing' | 'complete'>. Phase transitions drive conditional rendering of sub-components, not separate routes.
Demo mode pattern: When import.meta.env.VITE_DEMO_MODE === 'true', the API layer in lib/api.ts intercepts all calls and returns mock data after a simulated delay:

ts   const simulateDelay = () => new Promise(r => setTimeout(r, 800 + Math.random() * 700));

Token masking: Store full tokens in Zustand. Display only the masked version. Copy to clipboard always uses the full unmasked value. Never log tokens.
useReducedMotion() from Framer Motion — check at the animations.ts level. If true: set all duration: 0, strip all translate transforms. Keep opacity transitions only.
Timeline connecting line — use scaleY transform with transform-origin: top on a filled overlay div, not JS-driven height changes. CSS transforms are GPU-composited and won't cause layout recalc.
Service-color card transitions — transition box-shadow and border-color with CSS transition: all 600ms ease, not Framer Motion. This keeps it smooth without adding to the JS animation budget.
Score bars — IntersectionObserver: Bars start at width: 0. Observer fires once per bar when it enters viewport. Set a hasAnimated ref — never re-animate on scroll back. The observer should unobserve() after triggering.
React Router AnimatePresence — ensure mode="wait" so exit animation completes before enter begins. Each page must have a unique key — use the route pathname.
Password strength indicator on Sign Up: weak (<8 chars or no variety), medium (8+ chars, one type), strong (8+ chars, uppercase + lowercase + number/symbol). Animate bar width and color transition on every keystroke.
Error auto-dismiss: Use useEffect with setTimeout(5000) and clearTimeout on cleanup. Don't use react-hot-toast for inline form errors — only for transient system toasts (API failures, network errors).
The final standard: Every element on screen should feel like it was designed by a team that deeply respects developer attention. Nothing is loud. Nothing is wasted. The emptiness is intentional. The accent colors — green, blue, purple — are the only punctuation in a dark and precise system