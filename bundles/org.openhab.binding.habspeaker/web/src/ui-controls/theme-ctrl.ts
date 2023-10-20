import openHABLogoURL from "../icons/openhab-logo.svg?url";
export class ThemeCtrl {
    private logoUrl: string = openHABLogoURL;
    constructor(private logo: HTMLImageElement, private label: HTMLSpanElement) { }
    getLogoUrl() {
        return this.logoUrl;
    }
    setLabel(value: string) {
        this.label.textContent = value;
    }
    update(theme: { primaryColor?: string, secondaryColor?: string, tertiaryColor?: string, logoUrl?: string }) {
        if (theme.logoUrl) {
            this.logoUrl = theme.logoUrl;
        } else {
            this.logoUrl = openHABLogoURL;
        }
        this.logo.src = this.logoUrl;
        document.documentElement.style.setProperty('--color-primary', theme.primaryColor ?? 'var(--oh-red)');
        document.documentElement.style.setProperty('--color-secondary', theme.secondaryColor ?? 'var(--oh-grey)');
        document.documentElement.style.setProperty('--color-tertiary', theme.tertiaryColor ?? 'var(--hs-black)');
    }
}