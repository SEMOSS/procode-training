import { Animal, AnimalList } from '@/components';
import { useLoadingPixel, useSettingPixel } from '@/hooks';
import { Button, Stack, Typography } from '@mui/material';

/**
 * Renders a page for the animal example.
 *
 * @component
 */
export const AnimalPage = () => {
    /**
     * State
     */
    const [animalList, isAnimalListLoading, fetchAnimalList] = useLoadingPixel<
        Animal[]
    >('GetAnimals( )', []);
    const [addAnimal] = useSettingPixel();

    /**
     * Functions
     */
    const clickButton = async () => {
        addAnimal('1 + 3', () => fetchAnimalList());
    };

    return (
        <Stack>
            <Stack
                direction="row"
                alignItems="center"
                justifyContent="space-between"
            >
                <Typography variant="h4">Animal Page</Typography>
                <Button onClick={clickButton} variant="contained">
                    Add animal
                </Button>
            </Stack>
            <AnimalList animalList={isAnimalListLoading ? [] : animalList} />
        </Stack>
    );
};
