/**
 * Any dialog or confirm choice that is open on the page. The page tour waits to open until none is,
 * and the event tips skip an event that arrives while one is.
 */
export const OPEN_DIALOG_SELECTOR = '[role="dialog"],[role="alertdialog"]'
