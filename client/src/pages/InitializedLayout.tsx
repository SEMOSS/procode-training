import { MainNavigation } from '@/components';
import { useAppContext } from '@/contexts';
import { CircularProgress, Stack } from '@mui/material';
import { useInsight } from '@semoss/sdk-react';
import { Outlet } from 'react-router';

/**
 * Renders a loading wheel if SEMOSS is not initialized.
 *
 * @component
 */
export const InitializedLayout = () => {
    const { isInitialized } = useInsight();
    const { isAppDataLoading } = useAppContext();

    return (
        <Stack height="100vh">
            {/* Allow users to navigate around the app */}
            <MainNavigation />

            {isInitialized && !isAppDataLoading ? (
                // If initialized, set up padding and scroll
                <Stack padding={2} overflow="auto" height="100%">
                    {/* Outlet is a react router component; it allows the router to choose the child based on the route */}
                    <Outlet />
                </Stack>
            ) : (
                // Otherwise, show a centered loading wheel
                <Stack
                    height="100%"
                    alignItems="center"
                    justifyContent="center"
                >
                    <CircularProgress />
                </Stack>
            )}
        </Stack>
    );
};
