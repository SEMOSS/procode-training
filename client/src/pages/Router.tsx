import { HashRouter, Navigate, Route, Routes } from 'react-router';
import { ROUTE_PATH_PAGE_A } from './routes.constants';
import { HomePage } from './HomePage';
import { PageA } from './PageA';

export const Router = () => {
    return (
        <HashRouter>
            <Routes>
                <Route index element={<HomePage />} />

                <Route path={ROUTE_PATH_PAGE_A} element={<PageA />} />

                <Route path="*" element={<Navigate to="/" />} />
            </Routes>
        </HashRouter>
    );
};
