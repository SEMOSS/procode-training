import { Env } from '@semoss/sdk';
import { InsightProvider } from '@semoss/sdk-react';
import { AppContextProvider } from './contexts';
import { Router } from './pages';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { THEME } from './theme';

Env.update({
    MODULE: process.env.MODULE || '',
    ACCESS_KEY: process.env.ACCESS_KEY, // undefined in production
    SECRET_KEY: process.env.SECRET_KEY, // undefined in production
    APP: process.env.APP || '',
});

/**
 * Renders the SEMOSS React app.
 *
 * @component
 */
export const App = () => {
    return (
        // The InsightProvider starts a new Insight and sets the context to the current project. This components are imported from SEMOSS SDK
        <InsightProvider>
            {/* The AppContextProvider stores data specific to the current app, and runPixel.
            This component is custom to this project, and can be edited in AppContext.tsx */}
            <AppContextProvider>
                {/* The ThemeProvider and CssBaseline add MUI to provide styling. These components imported from MUI */}
                <ThemeProvider theme={THEME}>
                    <CssBaseline />

                    {/* The Router decides which page to render based on the url.
                    This component is custom to this project, and can be edited in Router.tsx */}
                    <Router />
                </ThemeProvider>
            </AppContextProvider>
        </InsightProvider>
    );
};
