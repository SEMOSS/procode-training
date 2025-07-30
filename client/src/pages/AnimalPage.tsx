import { AddAnimalModal, Animal, AnimalList } from '@/components';
import { useAppContext } from '@/contexts';
import { useLoadingPixel } from '@/hooks';
import { Button, Stack, Typography } from '@mui/material';
import { useState } from 'react';

/**
 * Renders a page for the animal example.
 *
 * @component
 */
export const AnimalPage = () => {
    const { setMessageSnackbarProps } = useAppContext();

    /**
     * State
     */
    const [animalList, isAnimalListLoading, fetchAnimalList] = useLoadingPixel<
        Animal[]
    >('GetAnimals( )', []);
    const [isAddAnimalModalOpen, setIsAddAnimalModalOpen] =
        useState<boolean>(false);

    /**
     * Functions
     */
    const handleModalClose = (addedAnimal: boolean) => {
        setIsAddAnimalModalOpen(false);
        if (addedAnimal) {
            fetchAnimalList();
        }
    };

    return (
        <Stack spacing={2}>
            <Stack
                direction="row"
                alignItems="center"
                justifyContent="space-between"
            >
                <Typography variant="h4">Animals</Typography>
                <Button
                    onClick={() => setIsAddAnimalModalOpen(true)}
                    variant="contained"
                >
                    Add animal
                </Button>
            </Stack>

            <AnimalList
                animalList={animalList ?? []}
                loading={isAnimalListLoading}
            />

            <AddAnimalModal
                open={isAddAnimalModalOpen}
                onClose={handleModalClose}
            />

            <Button
                onClick={() =>
                    setMessageSnackbarProps({
                        open: true,
                        message: 'This is a test message',
                        severity: 'success',
                    })
                }
            >
                click
            </Button>
        </Stack>
    );
};
