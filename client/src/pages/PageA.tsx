import { useAppContext } from '@/contexts';
import { usePixel } from '@/hooks';

/**
 * Renders a temporary test page.
 *
 * @component
 */
export const PageA = () => {
    const { runPixel } = useAppContext();

    const [output, isOutputLoading] = usePixel<number>('4 + 5');

    const clickButton = async () => {
        const response = await runPixel<number>('2 + 3');
        console.log(response);
    };

    // need to fix the login route thing

    return (
        <div>
            <div>Page A</div>
            <button onClick={clickButton}>Button</button>
            <div>{`4 + 5 = ${isOutputLoading ? 'loading' : output}`}</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
            <div>Hi</div>
        </div>
    );
};
