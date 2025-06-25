import { Button, Stack, styled, Typography, useTheme } from '@mui/material';
import { SemossBlueLogo } from '@/assets';
import { useNavigate } from 'react-router';
import { ROUTE_PATH_PAGE_A } from '@/pages/routes.constants';

// A Stack with a different-colored background
const StyledStack = styled(Stack)(({ theme }) => ({
    backgroundColor: theme.palette.background.paper,
    height: theme.spacing(8),
}));

// A Stack that changes the cursor to a finger to show the user that they can click
const CursorStack = styled(Stack)({
    cursor: 'pointer',
});

// The list of the buttons that should be displayed
const navigationButtons: {
    path: string;
    text: string;
}[] = [
    {
        path: '/',
        text: 'Home',
    },
    {
        path: ROUTE_PATH_PAGE_A,
        text: 'Page A',
    },
];

/**
 * The main navigation bar allowing users to move between pages.
 *
 * @component
 */
export const MainNavigation = () => {
    const { spacing } = useTheme();
    const navigate = useNavigate();

    return (
        <StyledStack
            direction="row"
            alignItems="center"
            spacing={2}
            paddingLeft={2}
        >
            {/* Display the logo and the title, and have clicking them take users home */}
            <CursorStack
                direction="row"
                alignItems="center"
                spacing={2}
                onClick={() => navigate('/')}
            >
                <img
                    src={SemossBlueLogo}
                    alt="Semoss Blue Logo"
                    height={spacing(6)}
                />
                <Typography variant="h4" fontWeight="bold">
                    SEMOSS Blank Canvas
                </Typography>
            </CursorStack>

            {/* Display the navigation buttons */}
            {navigationButtons.map((page) => (
                <Button
                    key={page.path}
                    onClick={() => navigate(page.path)}
                    color="inherit"
                >
                    {page.text}
                </Button>
            ))}
        </StyledStack>
    );
};
