package com.example.ui.testing

// Pixel 8 width and density, 12000 dp tall. A LazyColumn composes every item that fits the window, so with this window a screen like
// GiftScreen is placed in full at the first layout and a test never has to scroll it. Under Robolectric one real scroll of a lazy list is
// expensive (about 50 s of CPU for 60 plain rows): every scroll step waits for idle while Compose prefetch keeps posting frame callbacks,
// so the main looper never settles. A node that is already placed costs a millisecond or two.
// ponytail: GiftScreen is about 8900 dp tall today (20 ideas, checklist, reminders, footer). The tests do not fail when a screen outgrows
// this window, they get slow and flaky again; raise the height or scroll by index then.
const val TALL_PIXEL8 = "w411dp-h12000dp-normal-long-notround-any-420dpi-keyshidden-nonav"
