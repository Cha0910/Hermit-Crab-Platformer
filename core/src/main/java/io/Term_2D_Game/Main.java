package io.Term_2D_Game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;

import io.Term_2D_Game.Player.Player;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private GameWorld gameWorld;
    private final float WORLD_WIDTH = 1280;
    private final float WORLD_HEIGHT = 720;
    private FadeEffect fadeEffect;
    private ShapeRenderer shapeRenderer;
    private BitmapFont uiFont;
    private FreeTypeFontGenerator fontGenerator;
    private GlyphLayout layout = new GlyphLayout();
    private enum GameState{
        RUNNING,
        PAUSED,
        CLEARED,
        ENDED,
        DEAD
    }
    private GameState currentState;
    private CameraManager cameraManager;

    @Override
    public void create() {
        batch = new SpriteBatch();
        currentState = GameState.RUNNING;
        Assets.load();
        gameWorld = new GameWorld();
        cameraManager = new CameraManager(gameWorld, batch);
        shapeRenderer = new ShapeRenderer();
        fadeEffect = new FadeEffect(WORLD_WIDTH, WORLD_HEIGHT);
        fadeEffect.startFadeIn();

        fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Galmuri14.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter param = new FreeTypeFontGenerator.FreeTypeFontParameter();
        param.size = 32;
        param.color = Color.WHITE;
        param.borderWidth = 2f;
        param.borderColor = Color.BLACK;
        param.shadowOffsetX = 2;
        param.shadowOffsetY = 2;
        param.shadowColor = new Color(0, 0, 0, 0.5f);
        uiFont = fontGenerator.generateFont(param);
    }

    @Override
    public void render() {
        ScreenUtils.clear(1f, 1f, 1f, 1f);
        float delta = Gdx.graphics.getDeltaTime();

        input();

        if(currentState == GameState.RUNNING || currentState == GameState.CLEARED || currentState == GameState.DEAD){
            gameWorld.update(delta);
            cameraManager.updateCamera(delta);
        }
        checkState();
        fadeEffect.update(delta);
        draw();
    }

    @Override
    public void dispose() {
        batch.dispose();
        gameWorld.dispose();
        Assets.dispose();
        uiFont.dispose();
        fontGenerator.dispose();
    }

    private void draw(){
        batch.begin();
        gameWorld.draw(batch);
        batch.end();

        fadeEffect.render(shapeRenderer, gameWorld.getPlayer().getBody().getPosition(), cameraManager.getCamera());

        batch.begin();
        if (currentState == GameState.PAUSED) {
            drawCenteredText(batch, "PAUSE");
        } else if (currentState == GameState.ENDED) {
            drawCenteredText(batch, "GAME OVER\n Press 'R' to Restart");
        }
        batch.end();
    }

    private void input() {
        if(Gdx.input.isKeyJustPressed((Input.Keys.P))){
            pause();
        }

        if(currentState == GameState.RUNNING){
            if(Gdx.input.isKeyJustPressed((Input.Keys.R))){
                fadeEffect.startFadeIn();
                gameWorld.restartCurrentLevel();
            }
        }

        else if(currentState == GameState.ENDED){
            if(Gdx.input.isKeyJustPressed((Input.Keys.R))){
                gameWorld.setIsEnded(false);
                currentState = GameState.RUNNING;
                fadeEffect.startFadeIn();
                gameWorld.restartCurrentLevel();
            }
        }
    }

    public void pause(){
        if(currentState == GameState.RUNNING){
            currentState = GameState.PAUSED;
            fadeEffect.setPauseOverlay(true);
        }
        else if(currentState == GameState.PAUSED){
            currentState = GameState.RUNNING;
            fadeEffect.setPauseOverlay(false);
        }
    }

    public void checkState(){
        if(gameWorld.getIsEnded()){
            currentState = GameState.ENDED;
        }
        else if(gameWorld.getPlayer().getIsCleared() && currentState == GameState.RUNNING){
            currentState = GameState.CLEARED;
            fadeEffect.startFadeOut();
        }

        else if (!gameWorld.getPlayer().getIsCleared() && currentState == GameState.CLEARED) {
            currentState = GameState.RUNNING;
            fadeEffect.startFadeIn();
        }
        else if(gameWorld.getPlayer().getState() == Player.State.DEAD && currentState == GameState.RUNNING){
            currentState = GameState.DEAD;
            fadeEffect.startFadeOut();
        }

        else if(gameWorld.getPlayer().getState() == Player.State.SPAWN && currentState == GameState.DEAD){
            currentState = GameState.RUNNING;
            fadeEffect.startFadeIn();
        }
    }

    private void drawCenteredText(SpriteBatch batch, String text) {
        OrthographicCamera camera = cameraManager.getCamera();
        layout.setText(uiFont, text, Color.WHITE, 720f, Align.center, true);

        float x = camera.position.x - 720f / 2f;
        float y = camera.position.y + layout.height / 2f;
        uiFont.draw(batch, layout, x, y);
    }

}
