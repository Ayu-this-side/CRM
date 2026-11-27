# Page Override — CRM Workspace (all authenticated pages)

This overrides the generated `MASTER.md` **page pattern and palette**. The generated "Enterprise
Gateway" marketing pattern and its blue/amber palette do not apply to an internal, authenticated
sales workspace. Type pairing, density, motion tier, and anti-patterns remain in force.

## Product context

- **Users:** Admin (org-wide view, user management) and Sales Rep (own records only).
- **Core jobs:** move leads forward, track deals, clear today's follow-ups, inspect pipeline numbers.
- **Environment:** high-frequency desktop work, with occasional phone/tablet use.

## Layout pattern — "Black / Green Console"

- Persistent **near-black navigation rail** (236px desktop) with grouped sections, a green active
  indicator, and a user/footer block. Sections are block-level and stack vertically.
- A compact **dark command bar** at the top of the canvas: page context on the left, date and global
  task link on the right. No duplicated branding.
- Information is organized into **ruled sections**, not floating card collections. A section is a
  heading + hairline + content. Elevation is reserved for genuinely overlaid UI (drawer, dialog).
- Dashboard uses a **metric strip** (single divided band), then a 12-column asymmetric chart/table
  grid. Lists use dense tables with sticky headers and inline status, not one card per record.
- Detail pages use a two-column editorial split: primary record data on the left, related activity
  and actions on the right.

## Visual decisions

- **Mode:** dark by default. Near-black canvas `#050806`; the navigation rail is the darkest surface.
- **Palette:** emerald `#22C55E` (structure/links), mint `#86EFAC` (signal/accent), near-black
  `#050806` (canvas), deep green-black `#0A100C` (surfaces), light mint `#E9F7ED` (text). Status
  colors are restrained and always paired with a text label and a dot so meaning never depends on
  hue alone.
- **Shine:** controlled gloss on the brand mark, primary button, active nav item, and panel top edge.
  No neon glow, scanlines, or full-page gradients.
- **Type:** Fira Sans for UI/body; Fira Code for numerals, identifiers, dates and micro-labels.
  Body 16px / 1.5; table text 14px; micro-labels 11–12px uppercase with tracking.
- **Radius/elevation:** 6px controls, 10px panels, hairline borders, soft dark shadows.
- **Motion:** 120–200ms transform/opacity micro-interactions; 30–50ms stagger on first paint for
  at most two key regions. All motion is disabled under `prefers-reduced-motion: reduce`.

## Component rules

- One primary action per view. Destructive actions are text buttons separated from the action group.
- Status is a labelled pill (`dot` + text) using semantic tokens; no color-only encoding.
- Empty states include a plain-language explanation and the action that resolves them.
- Forms: persistent visible labels, helper text where useful, inline field errors with
  `aria-describedby`, focus moved to a linked error summary after a failed submit.
- Tables: `<caption class="sr-only">`, `<th scope="col">`, horizontally scrollable on small screens,
  row actions always visible (never hover-only).

## Accessibility checklist

- Skip link to `#main`; `aria-current="page"` on the active nav item.
- Focus ring: 2px solid accent with 2px offset, visible in both themes.
- Icon-only controls have `aria-label`; decorative SVG is `aria-hidden="true"`.
- All interactive targets ≥44px on touch viewports; hover is never the only affordance.
- Charts have a text summary and an accessible data table nearby.
