import { HashRouter, Navigate, Route, Routes } from 'react-router';
import { ROUTE_PATH_LOGIN_PAGE, ROUTE_PATH_PAGE_A } from './routes.constants';
import { InitializedLayout } from './InitializedLayout';
import { AuthorizedLayout } from './AuthorizedLayout';
import { HomePage } from './HomePage';
import { LoginPage } from './LoginPage';
import { PageA } from './PageA';

/**
 * Renders pages based on url.
 *
 * @component
 */
export const Router = () => {
    return (
        // Semoss projects typically use HashRouters
        <HashRouter>
            <Routes>
                {/* Wrap every route in InitializedLayout to ensure SEMOSS is ready to handle requests */}
                <Route element={<InitializedLayout />}>
                    {/* Wrap pages that should only be available to logged in users */}
                    <Route element={<AuthorizedLayout />}>
                        {/* If the path is empty, use the home page */}
                        <Route index element={<HomePage />} />

                        <Route path={ROUTE_PATH_PAGE_A} element={<PageA />} />
                    </Route>

                    {/* The login page should be available to non-logged in users (duh) */}
                    <Route
                        path={ROUTE_PATH_LOGIN_PAGE}
                        element={<LoginPage />}
                    />

                    {/* Any other urls should be sent to the home page */}
                    <Route path="*" element={<Navigate to="/" />} />
                </Route>
            </Routes>
        </HashRouter>
    );
};
