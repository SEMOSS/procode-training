import { Animal, AnimalList } from '@/components';
import { useAppContext } from '@/contexts';
import { useLoadingPixel } from '@/hooks';

/**
 * Renders a page for the animal example.
 *
 * @component
 */
export const AnimalPage = () => {
    const { runPixel } = useAppContext();

    const [animalList, isAnimalListLoading] = useLoadingPixel<Animal[]>(
        'GetAnimals( )',
        [],
    );

    const clickButton = async () => {
        const response = await runPixel<number>('2 + 3');
        console.log(response);
    };

    return (
        <div>
            <div>Animal Page</div>
            <button onClick={clickButton}>Button</button>
            <AnimalList animalList={isAnimalListLoading ? [] : animalList} />
        </div>
    );
};
