import { AnimalList } from '@/components';
import { useAppContext } from '@/contexts';
import { usePixel } from '@/hooks';

/**
 * Renders a page for the animal example.
 *
 * @component
 */
export const AnimalPage = () => {
    const { runPixel } = useAppContext();

    const [output, isOutputLoading] = usePixel<number>('4 + 5');

    const clickButton = async () => {
        const response = await runPixel<number>('2 + 3');
        console.log(response);
    };

    return (
        <div>
            <div>Animal Page</div>
            <button onClick={clickButton}>Button</button>
            <div>{`4 + 5 = ${isOutputLoading ? 'loading' : output}`}</div>
            <AnimalList
                animalList={[
                    {
                        animal_id: '1',
                        animal_name: 'Dog',
                        animal_type: 'Mammal',
                    },
                ]}
            />
        </div>
    );
};
