import { useInsight } from '@semoss/sdk-react';
import { Outlet } from 'react-router';

export const InitializedLayout = () => {
    const { isInitialized } = useInsight();

    if (!isInitialized) return <div>Loading</div>;

    return <Outlet />;
};
