package io.Term_2D_Game.Objects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

import io.Term_2D_Game.Assets;

public abstract class BlockObject {
    public Vector2 position;
    public Sprite sprite;
    public Body body;

    public static final float BlockWidth = 64f;
    public static final float BlockHeight = 64f;
    public static final float Overlap = 0f;
    public static final float PPM = 100f;

    /**
     * BlockObject 생성자
     * @param x 블록 X 좌표 (픽셀)
     * @param y 블록 Y 좌표 (픽셀)
     * @param spriteName JSON에서 가져온 키
     * @param box2dWorld Box2D 월드
     * @param isStatic 고정 블록 여부
     * @param isSensor 센서 블록 여부
     */
    public BlockObject(float x, float y, String spriteName, World box2dWorld, boolean isStatic, boolean isSensor) {
        this.position = new Vector2(x, y);

        this.sprite = new Sprite(Assets.get(spriteName));
        this.sprite.setSize(BlockWidth, BlockHeight);
        this.sprite.setPosition(x, y);

        BodyDef bodyDef = new BodyDef();
        bodyDef.position.set((x + BlockWidth / 2f) / PPM, (y + BlockHeight / 2f) / PPM);
        bodyDef.type = isStatic ? BodyDef.BodyType.StaticBody : BodyDef.BodyType.DynamicBody;
        this.body = box2dWorld.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox((BlockWidth / 2f + Overlap) / PPM, (BlockHeight / 2f) / PPM);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = isSensor;
        this.body.createFixture(fixtureDef);

        shape.dispose();
    }

    public void syncSpriteToBody() {
        Vector2 pos = body.getPosition();
        sprite.setPosition(pos.x * PPM - BlockWidth / 2f, pos.y * PPM - BlockHeight / 2f);
    }

    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        sprite.draw(batch);
    }
}
