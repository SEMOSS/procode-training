import { useInsight } from '@semoss/sdk-react';
import { Outlet } from 'react-router';

/**
 * Renders a loading wheel if SEMOSS is not initialized.
 *
 * @component
 */
export const InitializedLayout = () => {
    const { isInitialized } = useInsight();

    if (!isInitialized) return <div>Loading</div>;

    return <Outlet />;
};
