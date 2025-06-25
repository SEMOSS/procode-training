import { useInsight } from '@semoss/sdk-react';
import { Navigate } from 'react-router';

export const LoginPage = () => {
    const { isAuthorized } = useInsight();

    if (isAuthorized) return <Navigate to="/" />;

    return <div>Login page</div>;
};
