import { AddAnimalModal, Animal, AnimalList } from '@/components';
import { useLoadingPixel } from '@/hooks';
import { Button, Stack, Typography } from '@mui/material';
import { useState } from 'react';

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
        </Stack>
    );
};
