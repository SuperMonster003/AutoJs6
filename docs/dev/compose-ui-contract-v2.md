# Compose UI contract V2

F.4 adds the wide component set under `org.autojs.plugin.compose.api.v2.ComposeUiV2`.
`ComposeUiContract.CONTRACT_VERSION` is 2 and `MIN_SUPPORTED` stays 1. The minimum
host for a V2 renderer is `ComposeUiV2.REQUIRED_HOST_VERSION_CODE = 5322`.
The historical V1 deployment constant remains 5316.

## Negotiation and compatibility

The base factory reports version 2 both from `contractVersion()` and its capability
Bundle. Its component list contains the 30 original V1 entries and 25 new nodes,
for a total of 55. Snackbar remains command-only. The optional
`android-view-interop-v1` and `dialog-v1` factories keep their existing interfaces,
class-name capability keys and versions. The negotiated AndroidView union contains
56 entries, of which 55 are node factories.

The host checks minimum host version, factory version, capability version, shared
dependency fingerprint and component names in that order. A V1 provider can only
advertise names from `ComponentCatalog.V1`; a V2 provider can advertise names from
`ComposeUiV2.catalog`. AndroidView is added only after its optional factory is
negotiated. Empty or unknown component sets remain incompatible.

A new host loads an actual V1 provider and uses its original components. Creating
a V2 script handle is independent of the installed provider; submitting that handle
to a V1 provider fails with `UNKNOWN_COMPONENT` before the previous page is replaced.
An old host rejects a V2 package with `PLUGIN_INCOMPATIBLE`, including before class
loading when its version is less than 5322. No fallback advertises V2 as V1.

## Retained wire and new catalog

V1 model constructors, parcel tags, value kinds, error codes, modifiers, limits,
interfaces and catalog entries remain unchanged. The only changed existing public
literal is `ComposeUiContract.CONTRACT_VERSION`. V2 reuses the exact original
ComponentSpec and ModifierSpec objects; new literals live in the V2 namespace.
`ComponentCatalog.V1` and `AndroidViewInteropV1.catalog` remain frozen. The original
snapshot test allows only the reviewed version literal change during comparison.

`exportComposeUiCatalog` still produces the original contractVersion 1 projection.
`exportComposeUiInteropCatalog` remains the independent V1 AndroidView projection.
`exportComposeUiV2Catalog` produces `component-catalog-v2.json`, schemaVersion 1,
contractVersion 2, 55 entries and 176 properties. The declaration/documentation
generator verifies every retained V1 entry and generates the additions separately.

| Requested family | Supporting node |
| --- | --- |
| NavigationBar | NavigationBarItem |
| NavigationRail | NavigationRailItem |
| NavigationDrawer | NavigationDrawerItem |
| TabRow | Tab |
| DropdownMenu | DropdownMenuItem |
| SegmentedButton | SegmentedButtonItem |
| ModalBottomSheet, DatePicker, TimePicker, HorizontalPager, LazyVerticalGrid, AssistChip, FilterChip, InputChip, Badge, FloatingActionButton, SearchBar, Tooltip, PullToRefresh | None |

The JSON projection is authoritative for scalar types, defaults, named slots,
events and commands. Named slots retain their single-root rule, and all V1
shorthand properties and modifier ordering apply to V2 nodes.

## Prospective validation

Shared `ComposeUiV2.validateNode` and `validateTree` functions validate complete
prospective trees on both sides. The script graph validates before publication,
the core session before staging, and the renderer before committing its store.

- NavigationBar, NavigationRail, TabRow and SegmentedButton require at least one
  direct primary item child of the matching type. Items cannot occupy named slots
  or another parent. Detached item preparation is permitted; mounting a standalone
  scoped item fails with `SCOPE_MISMATCH`.
- NavigationBar and NavigationRail allow at most one selected item. SegmentedButton
  has this rule in single mode; multi mode permits multiple selections. Updating
  two item handles together uses the existing `compose.batch`.
- TabRow.selectedIndex and HorizontalPager.page identify a current child. Removing
  that child or shrinking the collection requires a valid resulting index in the
  same transaction.
- DatePicker accepts nullable, integral UTC-midnight epoch milliseconds inside its
  inclusive yearStart/yearEnd range. Supported years are 1583 through 9999, with UTC
  validation. The first complete Gregorian year avoids Material3's API 24/25
  Calendar cutover differing from the API 26+ java.time implementation. A null
  selection with a custom range starts at a displayed month within that range.
- TimePicker.hour is 0 through 23 and minute is 0 through 59. displayMode chooses
  the public picker or input presentation.
- LazyVerticalGrid.columns is an integer from 1 through 64. Spacing and padding
  are nonnegative; contentPadding accepts one, two or four dimensions.
- The frozen UiNode constructor gives new grid and pager nodes the ordinary 2000
  child maximum. V1 LazyColumn/LazyRow retain their 10000 child envelope. The live
  tree limit remains 5000 nodes. No parcel limit was raised.

A rejected graph change restores properties, children, ownership and callbacks.
A rejected renderer patch preserves the accepted generation and borrowed native
views. V2 uses the existing event queue and owning script dispatcher.

## Event arguments and scrolling

New event names and Bundle fields live in `ComposeUiV2.Events` and `.Fields`.
Callbacks retain the existing node receiver. Script arguments are positional:

| Event | Arguments |
| --- | --- |
| openChange | open: boolean |
| selectedChange | selected: boolean |
| expandedChange | expanded: boolean |
| dateChange | selectedDateMillis: number or null |
| displayModeChange | displayMode: picker or input |
| timeChange | hour: number, minute: number |
| pageChange | page: number |
| queryChange, search | text: string |
| refresh | No arguments |

An absent selectedDateMillis field represents no native selection and becomes
explicit script null. No nullable LONG payload type is added. The host rejects
malformed V2 booleans, dates, time bounds, indices and mode strings before invoking
callbacks. Base click, focus and scroll callbacks retain their V1 meanings.

Grid and pager reuse `UiCommand.ScrollTo`; index names a child and offset uses
physical pixels. Pager.page is an explicit navigation request when the accepted
property changes. Native scrolling and scrollTo report the settled position through
pageChange. An unrelated tree update does not reset paging or discard keyed state.
Scripts can mirror pageChange into state for a script-visible selected page.

Navigation items, Tab, DropdownMenuItem, chips, SegmentedButtonItem,
FloatingActionButton and SearchBar support the existing focus/blur commands.
Compound DatePicker and TimePicker keep their native internal focus behavior.
The existing mounted-node, actual composition and window-readiness checks apply;
a focus request does not promise that the software keyboard will appear.

Selection, open, query and refreshing properties are controlled by scripts. Native
callbacks request changes; scripts publish accepted values. DatePicker also emits
displayModeChange for its native picker/input toggle.

## Window ownership and verification

DropdownMenu and Tooltip use public Compose popup APIs anchored to their host view.
ModalBottomSheet uses the public Dialog and Material sheet composition established
in F.3, including pre-show overlay type selection. No private window API, reflection,
copied Material implementation or dependency upgrade is introduced. Activity,
compose.floaty and compose.dialog retain their existing owners and permissions.

Floating compositions provide an independent OnBackPressedDispatcherOwner before
attachment. An owned host View routes unconsumed legacy Back key pairs and registers
the public API 33+ system Back callback only while a lifecycle-active handler exists.
The registration is removed on detach, callback disablement and close. Drawer and
SearchBar therefore receive real Back events without borrowing an Activity owner or
changing native View listeners supplied by scripts. IME-consumed or canceled key
pairs cannot fall through as a second component dismissal.
The host's global predictive-Back opt-in policy is unchanged. When Android declines
an API 33+ callback registration under that policy, its compatibility Back key path
still reaches the owned View. The fallback focus target is established after actual
layout/window focus and never focuses an underlying search field by itself.

`compose.attach` in legacy raw/resizable windows uses the same Back integration
for its owned Compose child. Multiple floating containers keep independent owners:
the focused subtree receives Back, and attaching or updating another container does
not take focus from a caller View. Native focus changes update the scoped system
registration; touching a container can select it. Closing a session removes only
its owned child and leaves the caller's XML tree, listeners and other containers
available. Activity-backed containers retain the Activity's existing Back path.

Host device tests cover native navigation/tab/chip/segment interactions, seven
invalid updates with retained callbacks, popup/tooltip/sheet execution on Activity
and overlay windows, a sheet inside an overlay dialog with its actual native root
window type, an actual V1 provider, and an installed TypeScript Engine compiling
and executing V2 TSX mixed with AndroidView and compose.dialog. The stage evidence
records actual devices, results and unexecuted boundaries; a test's existence alone
does not claim that it passed.
