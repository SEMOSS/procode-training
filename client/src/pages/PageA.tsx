import { useAppContext } from '@/contexts';
import { usePixel } from '@/hooks';

export const PageA = () => {
    const { runPixel } = useAppContext();

    const [output, isOutputLoading] = usePixel<number>('4 + 5');

    const clickButton = async () => {
        const response = await runPixel<number>('2 + 3');
        console.log(response);
    };

    return (
        <div>
            <div>Page A</div>
            <button onClick={clickButton}>Button</button>
            <div>{`4 + 5 = ${isOutputLoading ? 'loading' : output}`}</div>
        </div>
    );
};
