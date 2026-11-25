# **Atoning Penchant**

A compatibility mod for **Caverns & Chasms + Penchant** on **NeoForge 1.21.1**.
The Atoning Table gains Penchant’s enchanting table mechanics and GUI, while enchantments must be paid for according to C&C rules.



## How it works

Slots, left to right: **Spinel**, **item**, **Lapis Lazuli** (or a Book to extract enchantments).

From Penchant: the enchantment list (scrolling, buttons, tooltips), enchantments unlocked by books in chiseled
bookshelves, the book requirement, the book counter and the grindstone indicator (extract enchantments onto a Book
when a disenchanter is nearby).

From C&C: each enchantment costs **1 Spinel + 1 Lapis Lazuli** and a share of the item's durability
(`min(100%, 25% * i + random * 27.5%)`, `i` = 1..3). The item can break, and it must have durability.

The **dagger** above the book (Atoning Table book entity) switches the difficulty. Click it to cycle:

| Level | Books required | Durability | Result |
|---|---|---|---|
| Easy (green) | A | 25-52% | level 1, no progress (like Penchant) |
| Normal (orange) | A + 25% of A | 50-77% | 30-75% of the way to the max level |
| Hard (red) | A + 50% of A | 75-100% | 30%: max level, otherwise 60-95% of the way |

`A` is Penchant's book requirement for the enchantment. Enchantments that cannot level up always use Easy.
The dagger fades out when there is no item in the item slot. All numbers are in `AtoningCosts.java`.

## Textures

`src/main/resources/assets/atoning_penchant/textures/gui/`

| File | Size |
|---|---|
| `container/atoning_table.png` (window in the top-left corner of a 256x256 canvas) | 206x172 |
| `sprites/container/atoning_table/button_{easy,normal,hard}[_highlighted].png`, `button_disabled.png` | 91x12 |
| `sprites/container/atoning_table/scroller.png` | 6x19 |
| `sprites/container/atoning_table/dagger.png` (cursor away), `dagger_highlighted.png` (cursor on it) | 21x39 |

The book is drawn at the red square of the window texture (centre 53, 30). All layout numbers are constants at the top of
`AtoningPenchantScreen.java` (`BOOK_*`, `DAGGER_*`, `LIST_*`).
