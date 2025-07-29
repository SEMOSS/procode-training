import { Animal } from './examples.types';

export interface AnimalListProps {
    animalList: Animal[];
}

/**
 * Display a list of animals.
 *
 * @component
 */
export const AnimalList = ({ animalList }: AnimalListProps) => {
    return (
        <div>
            {animalList.map((animal) => JSON.stringify(animal)).join(',')}
        </div>
    );
};
