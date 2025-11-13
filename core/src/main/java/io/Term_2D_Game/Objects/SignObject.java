package io.Term_2D_Game.Objects;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

public class SignObject extends BlockObject {
    private boolean isVisible = false;
    private String message;
    //private TextureRegion bubble;
    FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Galmuri14.ttf"));
    BitmapFont font;

    private float offsetY = 35f; // 말풍선 y 오프셋
    /**
     * @param x X 좌표 (픽셀)
     * @param y Y 좌표 (픽셀)
     * @param box2dWorld Box2D 월드
     * @param message 표지판 메시지
     */
    public SignObject(float x, float y, World box2dWorld ,String message) {
        super(x, y, "sign", box2dWorld, true, true);
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = 16;                   // 폰트 크기
        parameter.color = Color.WHITE;         // 색상
        parameter.borderWidth = 2f;            // 테두리 두께
        parameter.borderColor = Color.BLACK;   // 테두리 색상
        parameter.shadowOffsetX = 2;           // 그림자 X 오프셋
        parameter.shadowOffsetY = 2;           // 그림자 Y 오프셋
        parameter.shadowColor = new Color(0, 0, 0, 0.5f); // 그림자 색
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + message;
        font = generator.generateFont(parameter);
        generator.dispose();

        this.message = message;
        body.setUserData(this);
        for (Fixture f : body.getFixtureList()) {
            f.setUserData(this);
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        syncSpriteToBody();
        super.draw(batch);
        if(isVisible){
            drawSpeechBubble(batch);
        }
    }

    public void showMessage() {
        isVisible = true;
    }

    public void hideMessage(){
        isVisible = false;
    }

    private void drawSpeechBubble(SpriteBatch batch) {
        float x = this.position.x;
        float y = this.position.y + offsetY;

        // 텍스트 중앙정렬
        GlyphLayout layout = new GlyphLayout(font, message);
        float textX = x - layout.width / 6f;
        float textY = y + layout.height + BlockHeight;
        font.draw(batch, layout, textX, textY);
    }
}

