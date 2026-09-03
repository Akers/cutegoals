export enum PageEnum {
  // 登录（家长端 / 管理端独立入口；BASE_LOGIN 保留为模板兼容别名，指向家长端登录）
  BASE_LOGIN = '/parent/login',
  BASE_LOGIN_NAME = 'ParentLogin',
  PARENT_LOGIN = '/parent/login',
  ADMIN_LOGIN = '/admin/login',
  ADMIN_INIT = '/admin/init',
  //重定向
  REDIRECT = '/redirect',
  REDIRECT_NAME = 'Redirect',
  // 首页（家长端 / 管理端）
  BASE_HOME = '/parent',
  BASE_HOME_REDIRECT = '/parent/index',
  ADMIN_HOME = '/admin',
  ADMIN_HOME_REDIRECT = '/admin/index',
  // 错误
  ERROR_PAGE_NAME = 'ErrorPage',
}
