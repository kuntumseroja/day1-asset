import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Select, SelectItem, Stack, Tile } from '@carbon/react';
import { devLogin, keycloakLoginRedirect } from '../auth/auth';

// KF: BC-01.01 — OIDC login (Keycloak realm detp); dev bypass as Bank A
export function LoginScreen() {
  const navigate = useNavigate();
  const [devUser, setDevUser] = useState<'bankA' | 'bankB' | 'operator'>('bankA');

  const handleDevLogin = () => {
    devLogin(devUser);
    navigate('/issuance');
  };

  return (
    <Tile style={{ maxWidth: 480, margin: '4rem auto' }}>
      <Stack gap={6}>
        <h1>D-ETP Participant Portal</h1>
        <p>Sign in via Keycloak OIDC (realm <code>detp</code>).</p>

        <Select
          id="dev-user"
          labelText="Dev mode user"
          value={devUser}
          onChange={(e) => setDevUser(e.target.value as typeof devUser)}
        >
          <SelectItem value="bankA" text="Bank A (MAKER)" />
          <SelectItem value="bankB" text="Bank B (MAKER)" />
          <SelectItem value="operator" text="BI Operator" />
        </Select>

        <Button kind="primary" onClick={handleDevLogin}>
          Dev bypass login
        </Button>

        <Button kind="tertiary" onClick={keycloakLoginRedirect}>
          Keycloak OIDC (stub redirect)
        </Button>
      </Stack>
    </Tile>
  );
}
