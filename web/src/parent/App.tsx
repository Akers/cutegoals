import { APP_NAME, createRoleInfo } from '@shared/index';

const role = createRoleInfo('parent');

function ParentApp() {
  return (
    <div>
      <h1>{APP_NAME} - {role.label}</h1>
      <p>Parent</p>
    </div>
  );
}

export default ParentApp;
