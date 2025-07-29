import { useSettingPixel } from '@/hooks';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
} from '@mui/material';

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
    const [addAnimal] = useSettingPixel();

    /**
     * Functions
     */
    const handleSubmitClick = async () => {
        addAnimal('1 + 3', () => onClose(true));
    };

    return (
        <Dialog open={open} fullWidth maxWidth="sm">
            <DialogTitle>Add animal</DialogTitle>
            <DialogContent>Hello</DialogContent>
            <DialogActions>
                <Button onClick={handleSubmitClick} variant="contained">
                    Add animal
                </Button>
            </DialogActions>
        </Dialog>
    );
};
