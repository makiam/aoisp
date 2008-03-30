/* Copyright (C) 2008 by Peter Eastman

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.glrenderer;

import artofillusion.object.*;
import artofillusion.math.*;
import artofillusion.*;
import artofillusion.image.*;
import artofillusion.texture.*;

import javax.media.opengl.*;
import java.io.*;
import java.util.*;
import java.nio.*;

public class ShaderGenerator
{
  private Scene theScene;
  private Camera theCamera;
  private int lightingShaderId, vertexShaderId;
  private int uniformTextureProgramId;
  private int diffuseColorId, hilightColorId, emissiveColorId, roughnessId;
  int pixelBufferId;
  private HashMap<ImageMap, Integer> imageMap;
  private HashMap<TextureRecord, Integer> textureMap;
  private HashMap<Class, Integer> mappingMap;
  private TextureSpec spec;

  private static int GL_TEXTURE[] = new int[] {GL.GL_TEXTURE0, GL.GL_TEXTURE1,
      GL.GL_TEXTURE2, GL.GL_TEXTURE3, GL.GL_TEXTURE4, GL.GL_TEXTURE5,
      GL.GL_TEXTURE6, GL.GL_TEXTURE7, GL.GL_TEXTURE8, GL.GL_TEXTURE9,
      GL.GL_TEXTURE10, GL.GL_TEXTURE11, GL.GL_TEXTURE12};

  public ShaderGenerator(Scene theScene, Camera theCamera)
  {
    this.theScene = theScene;
    this.theCamera = theCamera;
    lightingShaderId = -1;
    vertexShaderId = -1;
    uniformTextureProgramId = -1;
    pixelBufferId = -1;
    imageMap = new HashMap<ImageMap, Integer>();
    textureMap = new HashMap<TextureRecord, Integer>();
    mappingMap = new HashMap<Class, Integer>();
    spec = new TextureSpec();
  }

  public void prepareShader(GL gl, ObjectInfo info) throws IOException
  {
    Texture texture = info.object.getTexture();
    if (texture instanceof ImageMapTexture)
    {
      ImageMapTexture tex = (ImageMapTexture) texture;
      int program = getImageMappedTextureProgram(gl, info.object.getTextureMapping());
      gl.glUseProgram(program);

      int textureIndex = 0;
      if (tex.diffuseColor.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.diffuseColor.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "DiffuseColorImage");
        gl.glUniform1i(image, textureIndex++);
      }
      if (tex.specularColor.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.specularColor.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "SpecularColorImage");
        gl.glUniform1i(image, textureIndex++);
      }
      if (tex.emissiveColor.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.emissiveColor.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "EmissiveColorImage");
        gl.glUniform1i(image, textureIndex++);
      }
      if (tex.roughness.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.roughness.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "RoughnessImage");
        gl.glUniform1i(image, textureIndex++);
      }
      if (tex.specularity.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.specularity.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "SpecularityImage");
        gl.glUniform1i(image, textureIndex++);
      }
      if (tex.shininess.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.shininess.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "ShininessImage");
        gl.glUniform1i(image, textureIndex++);
      }
      if (tex.bump.getImage() != null)
      {
        gl.glActiveTexture(GL_TEXTURE[textureIndex]);
        gl.glBindTexture(GL.GL_TEXTURE_2D, getImageId(gl, tex.bump.getImage(), tex.tileX, tex.tileY));
        int image = gl.glGetUniformLocation(program, "BumpImage");
        gl.glUniform1i(image, textureIndex++);
      }
      int transform = gl.glGetUniformLocation(program, "TextureTransform");
      Mat4 fromView = info.coords.toLocal().times(theCamera.getViewToWorld());
      fromView = new Mat4(-fromView.m11, fromView.m12, -fromView.m13, fromView.m14,
                          -fromView.m21, fromView.m22, -fromView.m23, fromView.m24,
                          -fromView.m31, fromView.m32, -fromView.m33, fromView.m34,
                          -fromView.m41, fromView.m42, -fromView.m43, fromView.m44);
      Mat4 mat;
      if (info.object.getTextureMapping() instanceof ProjectionMapping)
        mat = ((ProjectionMapping) info.object.getTextureMapping()).getTransform().times(fromView);
      else
        mat = ((NonlinearMapping2D) info.object.getTextureMapping()).getPreTransform().times(fromView);
      gl.glUniformMatrix4fv(transform, 1, true, new float [] {
        (float) mat.m11, (float) mat.m12, (float) mat.m13, (float) mat.m14,
        (float) mat.m21, (float) mat.m22, (float) mat.m23, (float) mat.m24,
        (float) mat.m31, (float) mat.m32, (float) mat.m33, (float) mat.m34,
        (float) mat.m41, (float) mat.m42, (float) mat.m43, (float) mat.m44}, 0);
    }
    else
    {
      gl.glUseProgram(getUniformTextureProgram(gl));
      texture.getAverageSpec(spec, theScene.getTime(), info.object.getAverageParameterValues());
      gl.glUniform3f(diffuseColorId, spec.diffuse.getRed(), spec.diffuse.getGreen(), spec.diffuse.getBlue());
      gl.glUniform3f(hilightColorId, spec.hilight.getRed(), spec.hilight.getGreen(), spec.hilight.getBlue());
      gl.glUniform3f(emissiveColorId, spec.emissive.getRed(), spec.emissive.getGreen(), spec.emissive.getBlue());
      gl.glUniform1f(roughnessId, (float) spec.roughness);
    }
  }

  private String readFile(String name) throws IOException
  {
    BufferedReader in = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(name)));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = in.readLine()) != null)
      sb.append(line).append('\n');
    return sb.toString();
  }

  private int createShader(GL gl, int type, String source) throws GLException
  {
    int shader = gl.glCreateShader(type);
    gl.glShaderSource(shader, 1, new String[] {source}, new int[] {source.length()}, 0);
    gl.glCompileShader(shader);
    int status[] = new int[1];
    gl.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0);
    if (status[0] != GL.GL_TRUE)
    {
      int size[] = new int [1];
      gl.glGetShaderiv(shader, GL.GL_INFO_LOG_LENGTH, size, 0);
      byte log[] = new byte[size[0]];
      gl.glGetShaderInfoLog(shader, size[0], size, 0, log, 0);
      throw new GLException("Shader failed to compile:\n"+source+"\n\nDetails:\n\n"+new String(log));
    }
    return shader;
  }

  private int createProgram(GL gl, int... shader)
  {
    int program = gl.glCreateProgram();
    for (int id : shader)
      gl.glAttachShader(program, id);
    gl.glLinkProgram(program);
    int status[] = new int[1];
    gl.glGetProgramiv(program, GL.GL_LINK_STATUS, status, 0);
    if (status[0] != GL.GL_TRUE)
    {
      int size[] = new int [1];
      gl.glGetProgramiv(program, GL.GL_INFO_LOG_LENGTH, size, 0);
      byte log[] = new byte[size[0]];
      gl.glGetProgramInfoLog(program, size[0], size, 0, log, 0);
      throw new GLException("Error linking program:\n\n"+new String(log));
    }
    return program;
  }

  private int getVertexShader(GL gl) throws IOException
  {
    if (vertexShaderId == -1)
      vertexShaderId = createShader(gl, GL.GL_VERTEX_SHADER, readFile("artofillusion/glrenderer/vertexShader.txt"));
    return vertexShaderId;
  }

  /** Create the shader which performs lighting calculations. */

  private int getLightingShader(GL gl) throws IOException
  {
    if (lightingShaderId != -1)
      return lightingShaderId;
    ArrayList<ObjectInfo> directionalLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> ambientDirectionalLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> pointLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> ambientPointLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> spotLights = new ArrayList<ObjectInfo>();
    ArrayList<ObjectInfo> ambientSpotLights = new ArrayList<ObjectInfo>();

    for (int i = 0; i < theScene.getNumObjects(); i++)
    {
      ObjectInfo info = theScene.getObject(i);
      if (!(info.object instanceof Light))
        continue;
      if (!info.visible)
        continue;
      Light light = (Light) info.object;
      if (light instanceof DirectionalLight)
      {
        if (light.getType() == Light.TYPE_AMBIENT)
          ambientDirectionalLights.add(info);
        else
          directionalLights.add(info);
      }
      if (light instanceof PointLight)
      {
        if (light.getType() == Light.TYPE_AMBIENT)
          ambientPointLights.add(info);
        else
          pointLights.add(info);
      }
      if (light instanceof SpotLight)
      {
        if (light.getType() == Light.TYPE_AMBIENT)
          ambientSpotLights.add(info);
        else
          spotLights.add(info);
      }
    }
    StringBuilder shader = new StringBuilder(readFile("artofillusion/glrenderer/shaderMainStart.txt"));
    RGBColor ambient = theScene.getAmbientColor().duplicate();
    for (ObjectInfo info : ambientDirectionalLights)
    {
      RGBColor light = new RGBColor();
      ((DirectionalLight) info.object).getLight(light, 0.0f);
      ambient.add(light);
    }
    shader.append("vec3 diffuseLight = vec3(").append(ambient.getRed()).append(", ").append(ambient.getGreen()).append(", ").append(ambient.getBlue()).append(");\n");
    Mat4 toView = theCamera.getWorldToView();
    toView = new Mat4(-toView.m11, -toView.m12, -toView.m13, -toView.m14,
                      toView.m21, toView.m22, toView.m23, toView.m24,
                      -toView.m31, -toView.m32, -toView.m33, -toView.m34,
                      toView.m41, toView.m42, toView.m43, toView.m44
    );
    if (pointLights.size() > 0)
    {
      shader.append("const int pointLightCount = ").append(pointLights.size()).append(";\n");
      shader.append("vec3 pointLightPos[").append(pointLights.size()).append("];\n");
      shader.append("vec3 pointLightColor[").append(pointLights.size()).append("];\n");
      shader.append("float pointLightDecayRate[").append(pointLights.size()).append("];\n");
      for (int i = 0; i < pointLights.size(); i++)
      {
        PointLight light = (PointLight) pointLights.get(i).object;
        Vec3 pos = toView.times(pointLights.get(i).coords.getOrigin());
        shader.append("pointLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("pointLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        shader.append("pointLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/pointLight.txt"));
    }
    if (ambientPointLights.size() > 0)
    {
      shader.append("const int ambientPointLightCount = ").append(ambientPointLights.size()).append(";\n");
      shader.append("vec3 ambientPointLightPos[").append(ambientPointLights.size()).append("];\n");
      shader.append("vec3 ambientPointLightColor[").append(ambientPointLights.size()).append("];\n");
      shader.append("float ambientPointLightDecayRate[").append(ambientPointLights.size()).append("];\n");
      for (int i = 0; i < ambientPointLights.size(); i++)
      {
        PointLight light = (PointLight) ambientPointLights.get(i).object;
        Vec3 pos = toView.times(ambientPointLights.get(i).coords.getOrigin());
        shader.append("ambientPointLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("ambientPointLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        shader.append("ambientPointLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/ambientPointLight.txt"));
    }
    if (directionalLights.size() > 0)
    {
      shader.append("const int directionalLightCount = ").append(directionalLights.size()).append(";\n");
      shader.append("vec3 directionalLightDir[").append(directionalLights.size()).append("];\n");
      shader.append("vec3 directionalLightHilightDir[").append(directionalLights.size()).append("];\n");
      shader.append("vec3 directionalLightColor[").append(directionalLights.size()).append("];\n");
      for (int i = 0; i < directionalLights.size(); i++)
      {
        DirectionalLight light = (DirectionalLight) directionalLights.get(i).object;
        Vec3 dir = toView.timesDirection(directionalLights.get(i).coords.getZDirection());
        shader.append("directionalLightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        dir.z -= 1.0;
        dir.normalize();
        shader.append("directionalLightHilightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("directionalLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/directionalLight.txt"));
    }
    if (spotLights.size() > 0)
    {
      shader.append("const int spotLightCount = ").append(spotLights.size()).append(";\n");
      shader.append("vec3 spotLightPos[").append(spotLights.size()).append("];\n");
      shader.append("vec3 spotLightColor[").append(spotLights.size()).append("];\n");
      shader.append("vec3 spotLightDir[").append(spotLights.size()).append("];\n");
      shader.append("float spotLightDecayRate[").append(spotLights.size()).append("];\n");
      shader.append("float spotLightCutoff[").append(spotLights.size()).append("];\n");
      shader.append("float spotLightExponent[").append(spotLights.size()).append("];\n");
      for (int i = 0; i < spotLights.size(); i++)
      {
        SpotLight light = (SpotLight) spotLights.get(i).object;
        Vec3 pos = toView.times(spotLights.get(i).coords.getOrigin());
        shader.append("spotLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("spotLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        Vec3 dir = toView.timesDirection(spotLights.get(i).coords.getZDirection());
        shader.append("spotLightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        shader.append("spotLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
        shader.append("spotLightCutoff[").append(i).append("] = ")
          .append(light.getAngleCosine()).append(";\n");
        shader.append("spotLightExponent[").append(i).append("] = ")
          .append(light.getExponent()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/spotLight.txt"));
    }
    if (ambientSpotLights.size() > 0)
    {
      shader.append("const int ambientSpotLightCount = ").append(ambientSpotLights.size()).append(";\n");
      shader.append("vec3 ambientSpotLightPos[").append(ambientSpotLights.size()).append("];\n");
      shader.append("vec3 ambientSpotLightColor[").append(ambientSpotLights.size()).append("];\n");
      shader.append("vec3 ambientSpotLightDir[").append(ambientSpotLights.size()).append("];\n");
      shader.append("float ambientSpotLightDecayRate[").append(ambientSpotLights.size()).append("];\n");
      shader.append("float ambientSpotLightCutoff[").append(ambientSpotLights.size()).append("];\n");
      shader.append("float ambientSpotLightExponent[").append(ambientSpotLights.size()).append("];\n");
      for (int i = 0; i < ambientSpotLights.size(); i++)
      {
        SpotLight light = (SpotLight) ambientSpotLights.get(i).object;
        Vec3 pos = toView.times(ambientSpotLights.get(i).coords.getOrigin());
        shader.append("ambientSpotLightPos[").append(i).append("] = vec3(")
          .append(pos.x).append(", ").append(pos.y).append(", ").append(pos.z).append(");\n");
        RGBColor color = light.getColor();
        color.scale(light.getIntensity());
        shader.append("ambientSpotLightColor[").append(i).append("] = vec3(")
          .append(color.getRed()).append(", ").append(color.getGreen()).append(", ").append(color.getBlue()).append(");\n");
        Vec3 dir = toView.timesDirection(ambientSpotLights.get(i).coords.getZDirection());
        shader.append("ambientSpotLightDir[").append(i).append("] = vec3(")
          .append(-dir.x).append(", ").append(-dir.y).append(", ").append(-dir.z).append(");\n");
        shader.append("ambientSpotLightDecayRate[").append(i).append("] = ")
          .append(light.getDecayRate()).append(";\n");
        shader.append("ambientSpotLightCutoff[").append(i).append("] = ")
          .append(light.getAngleCosine()).append(";\n");
        shader.append("ambientSpotLightExponent[").append(i).append("] = ")
          .append(light.getExponent()).append(";\n");
      }
      shader.append(readFile("artofillusion/glrenderer/lights/ambientSpotLight.txt"));
    }
    shader.append(readFile("artofillusion/glrenderer/shaderMainEnd.txt"));
    lightingShaderId = createShader(gl, GL.GL_FRAGMENT_SHADER, shader.toString());
    return lightingShaderId;
  }

  private int getUniformTextureProgram(GL gl) throws IOException
  {
    if (uniformTextureProgramId != -1)
      return uniformTextureProgramId;
    int fragmentShader = createShader(gl, GL.GL_FRAGMENT_SHADER, readFile("artofillusion/glrenderer/uniformTextureFragmentShader.txt"));
    uniformTextureProgramId = createProgram(gl, getVertexShader(gl), getLightingShader(gl), fragmentShader);
    diffuseColorId = gl.glGetUniformLocation(uniformTextureProgramId, "uniformDiffuseColor");
    hilightColorId = gl.glGetUniformLocation(uniformTextureProgramId, "uniformHilightColor");
    emissiveColorId = gl.glGetUniformLocation(uniformTextureProgramId, "uniformEmissiveColor");
    roughnessId = gl.glGetUniformLocation(uniformTextureProgramId, "uniformRoughness");
    return uniformTextureProgramId;
  }

  private int getImageMappedTextureProgram(GL gl, TextureMapping mapping) throws IOException
  {
    TextureRecord record = new TextureRecord(mapping.getTexture(), mapping.getClass());
    Integer id = textureMap.get(record);
    if (id != null)
      return id;
    ImageMapTexture tex = (ImageMapTexture) mapping.getTexture();
    StringBuilder shader = new StringBuilder();
    if (tex.diffuseColor.getImage() != null)
      shader.append("uniform sampler2D DiffuseColorImage;\n");
    if (tex.specularColor.getImage() != null)
      shader.append("uniform sampler2D SpecularColorImage;\n");
    if (tex.emissiveColor.getImage() != null)
      shader.append("uniform sampler2D EmissiveColorImage;\n");
    if (tex.roughness.getImage() != null)
      shader.append("uniform sampler2D RoughnessImage;\n");
    if (tex.specularity.getImage() != null)
      shader.append("uniform sampler2D SpecularityImage;\n");
    if (tex.shininess.getImage() != null)
      shader.append("uniform sampler2D ShininessImage;\n");
    if (tex.bump.getImage() != null)
      shader.append("uniform sampler2D BumpImage;\n");
    char component[] = new char[] {'r', 'g', 'b', 'a'};
    shader.append("void getTextureCoordinates(out vec3 coords);\n");
    shader.append("void getTexture(out vec3 diffuseColor, out vec3 hilightColor, out vec3 emissiveColor, out float roughness, inout vec3 normal) {\n");
    shader.append("vec3 textureCoords;\n");
    shader.append("getTextureCoordinates(textureCoords);\n");
    shader.append("roughness = ").append(tex.roughness.getValue()).append(";\n");
    if (tex.roughness.getImage() != null)
      shader.append("roughness *= texture2D(RoughnessImage, textureCoords.xy).").append(component[tex.roughness.getComponent()]).append(";\n");
    shader.append("float specularity = ").append(tex.specularity.getValue()).append(";\n");
    if (tex.specularity.getImage() != null)
      shader.append("specularity *= texture2D(SpecularityImage, textureCoords.xy).").append(component[tex.specularity.getComponent()]).append(";\n");
    shader.append("float shininess = ").append(tex.shininess.getValue()).append(";\n");
    if (tex.shininess.getImage() != null)
      shader.append("shininess *= texture2D(ShininessImage, textureCoords.xy).").append(component[tex.shininess.getComponent()]).append(";\n");
    RGBColor diffuse = tex.diffuseColor.getColor();
    shader.append("diffuseColor = (1.0-specularity)*vec3(").append(diffuse.getRed()).append(", ").
        append(diffuse.getGreen()).append(", ").append(diffuse.getBlue()).append(");\n");
    if (tex.diffuseColor.getImage() != null)
      shader.append("diffuseColor *= vec3(texture2D(DiffuseColorImage, textureCoords.xy));\n");
    RGBColor specular = tex.specularColor.getColor();
    shader.append("vec3 spec = vec3(").append(specular.getRed()).append(", ").
        append(specular.getGreen()).append(", ").append(specular.getBlue()).append(");\n");
    if (tex.specularColor.getImage() != null)
      shader.append("spec *= vec3(texture2D(SpecularColorImage, textureCoords.xy));\n");
    shader.append("hilightColor = shininess*spec;\n");
    RGBColor emissive = tex.emissiveColor.getColor();
    shader.append("emissiveColor = vec3(").append(emissive.getRed()).append(", ").
        append(emissive.getGreen()).append(", ").append(emissive.getBlue()).append(");\n");
    if (tex.emissiveColor.getImage() != null)
      shader.append("emissiveColor *= vec3(texture2D(EmissiveColorImage, textureCoords.xy));\n");
    if (tex.bump.getImage() != null && tex.bump.getValue() != 0.0)
    {
      shader.append("float bump1 = texture2D(BumpImage, textureCoords.xy).").append(component[tex.bump.getComponent()]).append(";\n");
      double dx = 1.0/tex.bump.getImage().getWidth();
      double dy = 1.0/tex.bump.getImage().getHeight();
      shader.append("float bump2 = texture2D(BumpImage, textureCoords.xy+vec2(").append(dx).append(", 0.0)).").append(component[tex.bump.getComponent()]).append(";\n");
      shader.append("float bump3 = texture2D(BumpImage, textureCoords.xy-vec2(0.0, ").append(dy).append(")).").append(component[tex.bump.getComponent()]).append(";\n");
      shader.append("vec3 bumpgrad = vec3((bump2-bump1)/").append(dx*25.0).append(", (bump3-bump1)/").append(dy*25.0).append(", 0.0);\n");
      shader.append("bumpgrad *= ").append(tex.bump.getValue()).append(";\n");
      shader.append("normal *= dot(bumpgrad, normal)+1.0;\n");
      shader.append("normal -= bumpgrad;\n");
      shader.append("normal = normalize(normal);\n");
    }
    shader.append("}");
    int fragmentShader = createShader(gl, GL.GL_FRAGMENT_SHADER, shader.toString());
    int program = createProgram(gl, getVertexShader(gl), getLightingShader(gl), getMappingShader(gl, mapping), fragmentShader);
    textureMap.put(record, program);
    return program;
  }

  private int getMappingShader(GL gl, TextureMapping mapping) throws IOException
  {
    Integer id = mappingMap.get(mapping.getClass());
    if (id != null)
      return id;
    StringBuilder shader = new StringBuilder();
    shader.append("varying vec4 Position;\n");
    shader.append("uniform mat4 TextureTransform;\n");
    shader.append("void getTextureCoordinates(out vec3 coords) {\n");
    if (mapping instanceof ProjectionMapping)
      shader.append("coords = (TextureTransform*Position).xyz;\n");
    else if (mapping instanceof CylindricalMapping)
    {
      CylindricalMapping map = (CylindricalMapping) mapping;
      shader.append("vec3 intermed = (TextureTransform*Position).xyz;\n");
      shader.append("float theta = atan(intermed.z, intermed.x);\n");
      Vec2 scale = map.getScale();
      double ax = -180.0/(Math.PI*scale.x);
      double ay = 1.0/scale.y;
      shader.append("coords = vec3(theta*").append(ax).append(", intermed.y*").append(ay).append("+(").append(map.getOffset()).append("), 0.0);\n");
    }
    else if (mapping instanceof SphericalMapping)
    {
      SphericalMapping map = (SphericalMapping) mapping;
      shader.append("vec3 intermed = (TextureTransform*Position).xyz;\n");
      shader.append("float theta = atan(intermed.z, intermed.x);\n");
      shader.append("float r2 = length(intermed);\n");
      shader.append("float phi = acos(intermed.y/r2);\n");
      Vec2 scale = map.getScale();
      double ax = -180.0/(Math.PI*scale.x);
      double ay = -180.0/(Math.PI*scale.y);
      double dy = map.getOffset()*(Math.PI/180.0);
      shader.append("coords = vec3(theta*").append(ax).append(", phi*").append(ay).append("+(").append(dy).append("), 0.0);\n");
    }
    shader.append("}");
    int fragmentShader = createShader(gl, GL.GL_FRAGMENT_SHADER, shader.toString());
    mappingMap.put(mapping.getClass(), fragmentShader);
    return fragmentShader;
  }

  private int getImageId(GL gl, ImageMap image, boolean wrapx, boolean wrapy)
  {
    Integer id = imageMap.get(image);
    if (id != null)
      return id;
    if (pixelBufferId == -1)
    {
      int bufferId[] = new int [1];
      gl.glGenBuffers(1, bufferId, 0);
      pixelBufferId = bufferId[0];
    }
    int width = image.getWidth(), height = image.getHeight(), components = image.getComponentCount();
    gl.glBindBuffer(GL.GL_PIXEL_UNPACK_BUFFER, pixelBufferId);
    gl.glBufferDataARB(GL.GL_PIXEL_UNPACK_BUFFER, width*height*components, null, GL.GL_STREAM_DRAW);
    ByteBuffer map = gl.glMapBuffer(GL.GL_PIXEL_UNPACK_BUFFER, GL.GL_WRITE_ONLY);
    map.order(ByteOrder.nativeOrder());
    double xscale = 1.0/width, yscale = 1.0/height;
    for (int row = 0; row < height; row++)
      for (int col = 0; col < width; col++)
        for (int component = 0; component < components; component++)
          map.put((byte) (255.0*image.getComponent(component, wrapx, wrapy, col*xscale, row*yscale, 0.0, 0.0)));
    gl.glUnmapBuffer(GL.GL_PIXEL_UNPACK_BUFFER);


    int textureId[] = new int[1];
    gl.glGenTextures(1, textureId, 0);
    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId[0]);
    gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    int format;
    if (components == 1)
      format = GL.GL_RED;
    else if (components == 3)
      format = GL.GL_RGB;
    else
      format = GL.GL_RGBA;
    gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, format, image.getWidth(), image.getHeight(), 0, format, GL.GL_UNSIGNED_BYTE, 0);
    imageMap.put(image, textureId[0]);
    return textureId[0];
  }

  /**
   * This class is used for storing the mapping of Textures to shaders.
   */

  private class TextureRecord
  {
    Texture texture;
    Class mapping;

    public TextureRecord(Texture texture, Class mapping)
    {
      this.texture = texture;
      this.mapping = mapping;
    }

    public int hashCode()
    {
      return texture.hashCode();
    }

    public boolean equals(Object obj)
    {
      TextureRecord rec = (TextureRecord) obj;
      return (texture == rec.texture && mapping == rec.mapping);
    }
  }
}
