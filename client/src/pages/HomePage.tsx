import { useAppContext } from '@/contexts';
import { Stack, styled } from '@mui/material';

const StyledStack = styled(Stack)(({ theme }) => ({
    backgroundColor: theme.palette.background.paper,
    borderRadius: theme.shape.borderRadius,
}));

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
    const { models } = useAppContext();

    return (
        <Stack width="100%" alignItems="center">
            <StyledStack
                padding={2}
                width={{ xs: '100%', md: '50%' }}
                alignItems="center"
            >
                {JSON.stringify(models)}
            </StyledStack>
        </Stack>
    );
};
