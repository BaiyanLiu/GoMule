<div align="center">

# GoMule, extended

**A [GoMule](https://github.com/silospen/gomule) fork for D2R 3.4: dark theme, shared stash auto-sort, roll columns, smarter search.**

<img src="screenshots/overview.png" alt="Modern dark theme with an ATMA stash showing Roll % and Roll columns" width="100%">

</div>

> [!NOTE]
> Only the fork's additions are listed here. Original notes: [`GoMule/README.txt`](GoMule/README.txt).

## Modern dark theme

- New default look, with gold accents and striped tables. Switch under **File > Switch Appearance**.
- Open files show in gold in the project tree.

## Shared stash auto-sort

<table>
<tr>
<td width="42%" valign="top">

**1. Rules:** right-click an ATMA stash, then **Sorting rules...**. An item must match every section you fill in.

</td>
<td><img src="screenshots/sort-rules.png" alt="Sorting rules for Set - Aldur.d2x" width="100%"></td>
</tr>
<tr>
<td valign="top">

**2. Sort:** press **Sort into ATMA stashes**.

</td>
<td><img src="screenshots/shared-stash.png" alt="Shared stash with Sort and Revert buttons" width="60%"></td>
</tr>
<tr>
<td valign="top">

**3. Preview:** confirm before anything moves. Items that match no stash, or more than one, stay put.

**4. Undo:** **Revert last sort** works until you save.

</td>
<td><img src="screenshots/sort-preview.png" alt="Sort preview listing items per stash" width="60%"></td>
</tr>
</table>

You can also drag a shared stash item onto an ATMA stash in the tree.

## Roll columns

| Column | Meaning | Example |
|---|---|---|
| **Roll %** | Average roll of all varying stats (uniques, sets, runewords) | `89%` |
| **Roll** | Points short of perfect, defense excluded | Annihilus 16/20, 5/10% → `-9` |

Sorting breaks ties by name, then worst roll, then lowest defense. Duplicates line up worst to best.

<div align="center">
<img src="screenshots/item-menu.png" alt="Stash sorted with Roll %, Roll and a custom Life column" width="55%">
</div>

## Custom columns

- Each stash (and the All view) keeps its own columns.
- Right-click a header to hide it or **Choose columns...**.
- Add built-in columns (ilvl, Rarity, Base, Sock, Def, Eth, Set, Roll, From...) or any property, such as `to Life`.
- Click a header to sort.

<p align="center">
<img src="screenshots/column-menu.png" alt="Header right-click menu" width="49%">
<img src="screenshots/columns-dialog.png" alt="Columns dialog" width="49%">
</p>

## Smart search

| Query | Finds |
|---|---|
| `ias res` | all words |
| `-eth` | without the word |
| `jah\|ber` | either word |
| `"cast rate"` | exact phrase |

- New **MinLvl** box, next to MaxLvl.
- **Filter...** now matches text literally.

<div align="center">
<img src="screenshots/smart-search.png" alt="All view filtered with a smart search and a level range" width="100%">
</div>

## Tree folders and the All view

<table>
<tr>
<td valign="top">

- Right-click a section or folder to add, rename or delete folders. Folders can nest.
- **Move to folder** files a character or stash.
- Untick **Show in All**, per stash or per folder, to hide it from All. Hidden stashes show in *italics*.

</td>
<td width="38%">
<img src="screenshots/folder-menu.png" alt="Folder menu" width="100%">
<img src="screenshots/stash-menu.png" alt="Stash menu" width="100%">
</td>
</tr>
</table>

## Delete items

Use **Delete**, the <kbd>Delete</kbd> key or right-click on one or more items. It always asks first, and can be turned off per project.

<p align="center"><img src="screenshots/delete-confirm.png" alt="Delete confirmation" width="32%"></p>

## Restore from backup

Each save keeps the last 3 versions as `.bak` files. Right-click a stash, then **Restore from backup...**. A restore can itself be undone.

<p align="center"><img src="screenshots/restore.png" alt="Restore from backup dialog" width="60%"></p>

## Fixes

- Loads D2RLoader / newer 3.4 saves (128-bit IDs on misc items).
- No 2.5M cap when moving gold from the shared stash to the bank.
- Shared stash shows total gold across all pages.
