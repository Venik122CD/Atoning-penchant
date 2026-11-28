# **Atoning Penchant**

A compatibility mod for **Caverns & Chasms + Penchant** on **NeoForge 1.21.1**.
The Atoning Table gains Penchant’s enchanting table mechanics and GUI, while enchantments must be paid for according to C&C rules.



## How it works

Slots, left to right: **Spinel**, **item**, **Lapis Lazuli** (or a Book to extract an enchantment). Empty slots show
the Spinel / Lapis / Book placeholder icons.

From Penchant: the enchantment list (scrolling, buttons, tooltips), enchantments unlocked by books in chiseled
bookshelves, the book requirement, the book counter and the grindstone indicator.

From C&C: the item pays with durability (`min(100%, 25% * i + random * 27.5%)`, `i` = 1..3), it can break, and it must
have durability.

The **dagger** above the book (it appears when an item makes the book open) switches the difficulty. Click it to cycle:

| Level | Lapis + Spinel | Books required | Durability | Result |
|---|---|---|---|---|
| Easy (green) | 1 + 1 | A | 25-52% | level 1, no progress (like Penchant) |
| Normal (orange) | 2 + 2 | A + 25% of A | 50-77% | 30-75% of the way to the max level |
| Hard (red) | 3 + 3 | A + 75% of A | 75-100% | 30%: max level, otherwise 60-95% of the way |

`A` is Penchant's book requirement for the enchantment. Treasure enchantments (`#minecraft:treasure`) cost **double**
lapis and spinel. Enchantments that cannot level up always use Easy. In each button the numbers on the right are:
books, lapis, spinel.

**Extracting an enchantment onto a Book** (Book in the lapis slot, enchanted item in the item slot, grindstone nearby):
costs **6 Spinel**, the item loses 25-50% of its max durability (it never breaks from this), and the Atoning Table
**explodes** exactly like the Bejeweled Anvil (same `SpinelBoom`, particles and sound). You keep the enchanted Book
and the damaged item.

All numbers are in `AtoningCosts.java`.

## Textures

`src/main/resources/assets/atoning_penchant/textures/gui/`

| File | Size |
|---|---|
| `container/atoning_table.png` (window in the top-left corner of a 256x256 canvas) | 206x172 |
| `sprites/container/atoning_table/button_{easy,normal,hard}[_highlighted].png`, `button_disabled.png` | 91x12 |
| `sprites/container/atoning_table/scroller.png` | 6x19 |

The book is drawn at the red square of the window texture (centre 53, 30). All layout numbers are constants at the top of
`AtoningPenchantScreen.java` (`BOOK_*`, `DAGGER_*`, `LIST_*`).

## Dagger (3D model)

The dagger is a real 3D model drawn like the book: `assets/atoning_penchant/models/entity/spinel_dagger.json`
(Blockbench export) with the texture `textures/entity/spinel_dagger.png` (16x16). Replace both files to change it.
The mod reads the JSON at runtime, so there is nothing to register.

- it bobs smoothly up and down (the same wave as the C&C dagger particle) and occasionally twitches left or right;
- hovering fills it with 50% white; clicking makes it spin once, clockwise (seen from above), and cycles the level;
- constants (`DAGGER_*`, `SPIN_TICKS`, `TWITCH_*`) are at the top of `AtoningPenchantScreen.java`.

