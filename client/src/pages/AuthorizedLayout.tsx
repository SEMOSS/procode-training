import { useInsight } from '@semoss/sdk-react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { ROUTE_PATH_LOGIN_PAGE } from './routes.constants';
import { useAppContext } from '@/contexts';
import { LoadingScreen } from '@/components';

/**
 * Renders pages if the user is logged in, otherwise sends them to the login page.
 *
 * @component
 */
export const AuthorizedLayout = () => {
    const { isAuthorized } = useInsight(); // Read whether the user is authorized
    // Get the curent route, so that if we are trying to log the user in, we can take them to where they were trying to go
    const { pathname } = useLocation();
    const { isAppDataLoading } = useAppContext();

    // If the user is not authorized, take them to the login page, and pass their intended route
    if (!isAuthorized)
        return (
            <Navigate to={ROUTE_PATH_LOGIN_PAGE} state={{ target: pathname }} />
        );

    // If the app data is still loading, show a loading screen
    if (isAppDataLoading) return <LoadingScreen />;

    // Outlet is a react router component; it allows the router to choose the child based on the route
    return <Outlet />;
};
