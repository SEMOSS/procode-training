import { useSettingPixel } from '@/hooks';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    TextField,
} from '@mui/material';
import { useState } from 'react';

export interface AddAnimalModalProps {
    open: boolean;
    onClose: (addedAnimal: boolean) => void;
}

/**
 * Renders a modal to add an animal.
 *
 * @component
 */
export const AddAnimalModal = ({ open, onClose }: AddAnimalModalProps) => {
    const [addAnimal, isLoading] = useSettingPixel();

    /**
     * State
     */
    const [animalName, setAnimalName] = useState<string>('');
    const [animalType, setAnimalType] = useState<string>('');

    /**
     * Functions
     */
    const handleSubmitClick = async () => {
        addAnimal(
            `AddAnimal(animal_name=${JSON.stringify(animalName)}, animal_type=${JSON.stringify(animalType)})`,
            () => onClose(true),
        );
    };

    /**
     * Constants
     */
    const isReadyToSubmit =
        animalName.trim().length > 0 && animalType.trim().length > 0;

    return (
        <Dialog open={open} fullWidth maxWidth="sm">
            <DialogTitle>Add animal</DialogTitle>

            <DialogContent>
                <Stack spacing={2}>
                    {/* div to prevent title clipping */}
                    <div />

                    <TextField
                        value={animalName}
                        onChange={(e) => setAnimalName(e.target.value)}
                        label="Name"
                    />

                    <TextField
                        value={animalType}
                        onChange={(e) => setAnimalType(e.target.value)}
                        label="Type"
                    />
                </Stack>
            </DialogContent>

            <DialogActions>
                <Button
                    onClick={handleSubmitClick}
                    variant="contained"
                    loading={isLoading}
                    disabled={!isReadyToSubmit}
                >
                    Add animal
                </Button>
            </DialogActions>
        </Dialog>
    );
};
