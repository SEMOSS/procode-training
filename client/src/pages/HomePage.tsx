import { useAppContext } from '@/contexts';
import {
    Autocomplete,
    Stack,
    styled,
    TextField,
    Typography,
} from '@mui/material';

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
    const { models, databases } = useAppContext();

    return (
        <Stack width="100%" alignItems="center">
            <StyledStack
                padding={2}
                width={{ xs: '100%', md: '50%' }}
                spacing={1}
            >
                <Typography variant="h6" fontWeight="bold">
                    Options
                </Typography>
                <Autocomplete
                    options={models}
                    getOptionKey={(model) => model.app_id}
                    getOptionLabel={(model) => model.app_name}
                    renderInput={(params) => (
                        <TextField {...params} label="Model" />
                    )}
                    fullWidth
                />
                <Autocomplete
                    options={databases}
                    getOptionKey={(db) => db.app_id}
                    getOptionLabel={(db) => db.app_name}
                    renderInput={(params) => (
                        <TextField {...params} label="Database" />
                    )}
                    fullWidth
                />
            </StyledStack>
        </Stack>
    );
};
