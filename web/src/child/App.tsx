import { APP_NAME, createRoleInfo } from '@shared/index';

const role = createRoleInfo('child');

function ChildApp() {
  return (
    <div>
      <h1>{APP_NAME} - {role.label}</h1>
      <p>Child</p>
    </div>
  );
}

export default ChildApp;
