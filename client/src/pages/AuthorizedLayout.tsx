import { useInsight } from '@semoss/sdk-react';
import { Navigate, Outlet } from 'react-router';
import { ROUTE_PATH_LOGIN_PAGE } from './routes.constants';

/**
 * Renders pages if the user is logged in, otherwise sends them to the login page.
 *
 * @component
 */
export const AuthorizedLayout = () => {
    const { isAuthorized } = useInsight();

    if (!isAuthorized) return <Navigate to={ROUTE_PATH_LOGIN_PAGE} />;

    return <Outlet />;
};
