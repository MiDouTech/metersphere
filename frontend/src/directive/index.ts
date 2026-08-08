import { App } from 'vue';

import operablePermission from './operablePermission';
import outerClick from './outerClick';
import permission from './permission';
import validateExpiration from './validateExpiration';
import validateLicense from './validateLicense';
import visiblePermission from './visiblePermission';

export default {
  install(Vue: App) {
    Vue.directive('permission', permission);
    Vue.directive('visible-permission', visiblePermission);
    Vue.directive('operable-permission', operablePermission);
    Vue.directive('xpack', validateLicense);
    Vue.directive('expire', validateExpiration);
    Vue.directive('outer', outerClick);
  },
};
