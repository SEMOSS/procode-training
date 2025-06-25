import { HashRouter, Navigate, Route, Routes } from 'react-router';
import { ROUTE_PATH_LOGIN_PAGE, ROUTE_PATH_PAGE_A } from './routes.constants';
import { InitializedLayout } from './InitializedLayout';
import { AuthorizedLayout } from './AuthorizedLayout';
import { HomePage } from './HomePage';
import { LoginPage } from './LoginPage';
import { PageA } from './PageA';

export const Router = () => {
    return (
        <HashRouter>
            <Routes>
                <Route element={<InitializedLayout />}>
                    <Route element={<AuthorizedLayout />}>
                        <Route index element={<HomePage />} />

                        <Route path={ROUTE_PATH_PAGE_A} element={<PageA />} />
                    </Route>

                    <Route
                        path={ROUTE_PATH_LOGIN_PAGE}
                        element={<LoginPage />}
                    />

                    <Route path="*" element={<Navigate to="/" />} />
                </Route>
            </Routes>
        </HashRouter>
    );
};
