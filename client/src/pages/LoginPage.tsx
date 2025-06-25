import { useInsight } from '@semoss/sdk-react';
import { Navigate } from 'react-router';

/**
 * Renders a the login page if the user is not already logged in, otherwise sends them to the home page.
 *
 * @component
 */
export const LoginPage = () => {
    const { isAuthorized } = useInsight();

    if (isAuthorized) return <Navigate to="/" />;

    return <div>Login page</div>;
};
