import { Animal, AnimalList } from '@/components';
import { useLoadingPixel, useSettingPixel } from '@/hooks';

/**
 * Renders a page for the animal example.
 *
 * @component
 */
export const AnimalPage = () => {
    const [animalList, isAnimalListLoading] = useLoadingPixel<Animal[]>(
        'GetAnimals( )',
        [],
    );

    const [addAnimal] = useSettingPixel();

    const clickButton = async () => {
        addAnimal('1 + 3', (response) => console.log(response));
    };

    return (
        <div>
            <div>Animal Page</div>
            <button onClick={clickButton}>Button</button>
            <AnimalList animalList={isAnimalListLoading ? [] : animalList} />
        </div>
    );
};
