export function queryElement<T extends Element>(selector: string, parent?: HTMLElement | DocumentFragment) {
    const el = (parent ?? document).querySelector<T>(selector);
    if (el == null) {
        throw new Error("Missing required element: " + selector);
    }
    return el;
}