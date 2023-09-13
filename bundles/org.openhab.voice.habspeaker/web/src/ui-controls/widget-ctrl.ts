import microphoneIconSvg from "../icons/microphone.svg?raw";
import speakerIconSvg from "../icons/speaker.svg?raw";
import { queryElement } from "./utils";
export class WidgetCtrl {
    private readonly iconAnchor: HTMLAnchorElement;
    private mini = false;
    private online = false;
    private listening = false;
    private speaking = false;
    private onClick?: () => void;
    constructor(private button: HTMLButtonElement) {
        this.iconAnchor =  queryElement("#speaker_icon", button);
        button.addEventListener("click", () => this.online && this.onClick?.())
    }
    setOnline(value: boolean) {
        this.online = value;
        this.updateIcon();
    }
    setSpeaking(value: boolean) {
        this.speaking = value;
        this.updateIcon();
    }
    setListening(value: boolean) {
        this.listening = value;
        this.updateIcon();
    }
    setMini(value: boolean) {
        if(value && !this.mini) {
            this.mini = true;
            this.button.classList.add("mini-widget");
            console.debug('enable mini-widget mode');
        } else if(!value && this.mini) {
            this.mini = false;
            this.button.classList.remove("mini-widget");
            console.debug('disable mini-widget mode');
        }
    }
    updateIcon() {
        if (this.online) {
            this.button.disabled = false;
            if (this.speaking) {
                this.iconAnchor.innerHTML = speakerIconSvg;
            } else if (this.listening) {
                this.iconAnchor.innerHTML = microphoneIconSvg;
            } else {
                this.iconAnchor.innerHTML = "";
            }
        } else {
            this.button.disabled = true;
            this.iconAnchor.innerHTML = "";
        }
    }
    setOnClick(cb: () => void) {
        this.onClick = cb
    }
}