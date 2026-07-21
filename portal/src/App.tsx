import type { ReactNode } from 'react';
import { Navigate, Route, Routes, Link, useNavigate } from 'react-router-dom';
import {
  Content,
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  HeaderGlobalBar,
  HeaderGlobalAction,
  Theme,
} from '@carbon/react';
import { Logout } from '@carbon/icons-react';
import { isAuthenticated, logout, getStoredUser } from './auth/auth';
import { LoginScreen } from './screens/LoginScreen';
import { IssuanceScreen } from './screens/IssuanceScreen';
import { FafoQueueScreen } from './screens/FafoQueueScreen';
import { LimitsScreen } from './screens/LimitsScreen';
import { TransactionDetailScreen } from './screens/TransactionDetailScreen';
import { ReconScreen } from './screens/ReconScreen';
import { useSettlementEvents } from './hooks/useSettlementEvents';

function RequireAuth({ children }: { children: ReactNode }) {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function AppLayout() {
  const navigate = useNavigate();
  const user = getStoredUser();
  const { connected } = useSettlementEvents();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <>
      <Header aria-label="D-ETP Portal">
        <HeaderName as={Link} to="/issuance" prefix="D-ETP">
          Participant Portal
        </HeaderName>
        <HeaderNavigation aria-label="Main navigation">
          <HeaderMenuItem as={Link} to="/issuance">
            Issuance
          </HeaderMenuItem>
          <HeaderMenuItem as={Link} to="/queue">
            FAFO Queue
          </HeaderMenuItem>
          <HeaderMenuItem as={Link} to="/limits">
            Limits
          </HeaderMenuItem>
          <HeaderMenuItem as={Link} to="/recon">
            Recon
          </HeaderMenuItem>
        </HeaderNavigation>
        <HeaderGlobalBar>
          <span style={{ padding: '0 1rem', fontSize: '0.875rem' }}>
            {user?.displayName ?? 'Guest'} · WS {connected ? 'live' : '…'}
          </span>
          <HeaderGlobalAction aria-label="Logout" onClick={handleLogout}>
            <Logout size={20} />
          </HeaderGlobalAction>
        </HeaderGlobalBar>
      </Header>
      <Content style={{ padding: '2rem' }}>
        <Routes>
          <Route path="/issuance" element={<IssuanceScreen />} />
          <Route path="/queue" element={<FafoQueueScreen />} />
          <Route path="/limits" element={<LimitsScreen />} />
          <Route path="/recon" element={<ReconScreen />} />
          <Route path="/transactions/:uetr" element={<TransactionDetailScreen />} />
          <Route path="*" element={<Navigate to="/issuance" replace />} />
        </Routes>
      </Content>
    </>
  );
}

export default function App() {
  return (
    <Theme theme="g100">
      <Routes>
        <Route path="/login" element={<LoginScreen />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        />
      </Routes>
    </Theme>
  );
}
