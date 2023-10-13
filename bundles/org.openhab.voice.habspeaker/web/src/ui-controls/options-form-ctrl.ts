import { Platform } from "../platforms";
import { queryElement } from "./utils";

export class OptionsFormCtrl {
    constructor(private appRoot: HTMLElement, private configPanelTemplate: HTMLTemplateElement, private platform: Platform) { }
    async open(message: string, cb: () => Promise<void>) {
        const formContainer = queryElement<HTMLDivElement>("#config_form_container", this.configPanelTemplate.content)
            .cloneNode(true).firstChild?.parentElement as HTMLDivElement;
        const p = queryElement<HTMLParagraphElement>("#config_form_msg", formContainer);
        p.textContent = message;
        queryElement<HTMLInputElement>('#config_form_container input[name="speaker-id"]', formContainer).value = await this.platform.getSpeakerId() ?? ""
        const serverOptions = queryElement<HTMLParagraphElement>("#form_server_options", formContainer);
        const serverOptionsNeeded = await this.platform.isServerTokenNeeded();
        if (!serverOptionsNeeded) {
            serverOptions.remove();
        } else {
            queryElement<HTMLInputElement>('input[name="oh-url"]', serverOptions).value = await this.platform.getUrlOpenHAB() ?? ""
            queryElement<HTMLInputElement>('input[name="oh-token"]', serverOptions).value = await this.platform.getServerToken() ?? "";
        }
        formContainer.addEventListener("submit", async (ev: Event) => {
            const data = new FormData(ev.target as HTMLFormElement);
            const value = Object.fromEntries(data.entries()) as { "speaker-id": string, "oh-url"?: string, "oh-token"?: string, };
            this.appRoot.removeChild(formContainer);
            await this.platform.setLocalSettings({
                speakerId: value["speaker-id"],
                ohUrl: value["oh-url"],
                ohToken: value["oh-token"],
            });
            await cb();
        });
        this.appRoot.appendChild(formContainer);
    }
}