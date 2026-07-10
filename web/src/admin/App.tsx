import { APP_NAME, createRoleInfo } from '@shared/index';

const role = createRoleInfo('admin');

function AdminApp() {
  return (
    <div>
      <h1>{APP_NAME} - {role.label}</h1>
      <p>Admin</p>
    </div>
  );
}

export default AdminApp;
