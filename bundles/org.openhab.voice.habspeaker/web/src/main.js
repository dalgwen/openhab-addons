import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import "./assets/main.css";
import { library } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { faPlay, faPause, faForward, faBackward, faFastForward, faFastBackward } from '@fortawesome/free-solid-svg-icons'

// load fa icons
library.add(faPlay, faPause, faForward, faBackward, faFastForward, faFastBackward);

createApp(App)
    .component('fa-icon', FontAwesomeIcon)
    .use(createPinia())
    .use(router)
    .mount("#app");

