/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doubleclick.openrtb;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.doubleclick.DcExt;
import com.google.doubleclick.Doubleclick;
import com.google.doubleclick.Doubleclick.BidRequest.AdSlot;
import com.google.doubleclick.Doubleclick.BidResponse.Ad;
import com.google.openrtb.OpenRtb;
import com.google.openrtb.OpenRtb.BidRequest;
import com.google.openrtb.OpenRtb.BidRequest.Impression;
import com.google.openrtb.OpenRtb.BidRequest.Impression.Banner;
import com.google.openrtb.OpenRtb.BidRequest.Impression.PMP;
import com.google.openrtb.OpenRtb.BidResponse;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid;
import com.google.openrtb.OpenRtb.BidResponse.SeatBid.Bid;
import com.google.openrtb.OpenRtb.Flag;

import com.codahale.metrics.MetricRegistry;

import org.junit.Test;

/**
 * Tests for {@link DoubleClickOpenRtbMapper}.
 */
public class DoubleClickOpenRtbMapperTest {
  public static final String DEFAULT_CALLBACK_URL = "http://localhost";

  private DoubleClickOpenRtbMapper mapper = new DoubleClickOpenRtbMapper(
      new MetricRegistry(),
      TestUtil.getMetadata(),
      ImmutableList.of(DoubleClickLinkMapper.INSTANCE));

  @Test
  public void testResponse() {
    Doubleclick.BidResponse.Builder dcResponse = mapper.toNative(
        TestUtil.newBidRequest("1", 1, 1, 100).build(),
        BidResponse.newBuilder()
            .setBidid("bidid")
            .addSeatbid(SeatBid.newBuilder()
                .addBid(TestData.newBid(false)))
            .build());
    assertNotNull(dcResponse);
    assertEquals("bidid", dcResponse.getDebugString());
    assertEquals(1, dcResponse.getAdCount());
    Doubleclick.BidResponse.Ad ad = dcResponse.getAd(0);
    assertEquals(1, ad.getAdslotCount());
    assertEquals(1, ad.getAdslot(0).getId());
    assertFalse(ad.getAdslot(0).hasAdgroupId());
    assertEquals(1000, ad.getAdslot(0).getMaxCpmMicros());
  }

  @Test(expected = MapperException.class)
  public void testResponse_nonExistingImp() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(Doubleclick.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .addAdslot(AdSlot.newBuilder().setId(5))
        .addAdslot(AdSlot.newBuilder().setId(10)));
    mapper.toNative(request, TestUtil.newBidResponse(TestData.newBid(false)));
  }

  @Test(expected = MapperException.class)
  public void testResponse_impWithoutAd() {
    OpenRtb.BidRequest request = OpenRtb.BidRequest.newBuilder()
        .setId("1")
        .addImp(Impression.newBuilder()
            .setId("1"))
        .build();
    Bid bid = TestData.newBid(false)
        .setAdm("snippet")
        .build();
    Doubleclick.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
  }

  @Test
  public void testResponse_attributeCheck() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("adId")
        .setAdm("snippet")
        .build();

    Doubleclick.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
  }

  @Test
  public void testResponse_htmlSnippetAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("adId")
        .setCrid("creativeId")
        .setAdm("<img src=\"foo\">")
        .build();
    Doubleclick.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
    assertEquals("<img src=\"foo\">", ad.getHtmlSnippet());
    assertEquals("creativeId", ad.getBuyerCreativeId());
    assertTrue(!ad.hasVideoUrl());
  }

  @Test
  public void testResponse_videoAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(
        TestData.newRequest(0, false).setVideo(TestData.newVideo(0)));
    Bid bid = TestData.newBid(false)
        .setAdm("http://my-video")
        .setCrid("creativeId")
        .build();
    Doubleclick.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
    assertEquals("http://my-video", ad.getVideoUrl());
    assertEquals("creativeId", ad.getBuyerCreativeId());
    assertTrue(!ad.hasHtmlSnippet());
  }

  @Test
  public void testResponse_htmlSnippetTemplateAd() {
    OpenRtb.BidRequest request = TestUtil.newBidRequest(TestData.newRequest());
    Bid bid = TestData.newBid(false)
        .setAdid("99")
        .setCrid("creativeId")
        .setAdm("<img src=\"foo-templatized\">")
        .setExtension(DcExt.ad,
            Doubleclick.BidResponse.Ad.newBuilder()
                .addTemplateParameter(Doubleclick.BidResponse.Ad.TemplateParameter.newBuilder())
                .build())
        .build();
    Doubleclick.BidResponse.Ad.Builder ad = mapper.buildResponseAd(
        request, TestUtil.newBidResponse(bid), bid);
    assertNotNull(ad);
    assertFalse(ad.hasHtmlSnippet());
    assertEquals("<img src=\"foo-templatized\">", ad.getSnippetTemplate());
    assertEquals(1, ad.getTemplateParameterCount());
    assertEquals("creativeId", ad.getBuyerCreativeId());
    assertTrue(!ad.hasVideoUrl());
  }

  @Test(expected = MapperException.class)
  public void testResponse_noCrid() {
    Bid bid = TestData.newBid(false)
        .clearCrid()
        .build();
    mapper.buildResponseAd(
        TestUtil.newBidRequest(TestData.newRequest()),
        TestUtil.newBidResponse(bid),
        bid);
  }

  @Test
  public void testResponse_multisizeBannerGood() {
    Impression.Builder imp = Impression.newBuilder()
        .setId("1")
        .setBanner(Banner.newBuilder().setId("1")
            .setWmin(100).setWmax(200)
            .setHmin(300).setHmax(400))
        .setExtension(DcExt.adSlot, Doubleclick.BidRequest.AdSlot.newBuilder()
            .setId(0)
            .addAllWidth(asList(100, 200))
            .addAllHeight(asList(300, 400)).build());
    Bid bid1 = TestData.newBid(false).build();
    Bid bid2 = TestData.newBid(true).build();
    BidResponse response = TestUtil.newBidResponse(bid1, bid1);
    OpenRtb.BidRequest request = OpenRtb.BidRequest.newBuilder().setId("1").addImp(imp).build();
    Doubleclick.BidResponse.Ad.Builder ad1 = mapper.buildResponseAd(request, response, bid1);
    assertTrue(!ad1.hasWidth() && !ad1.hasHeight());
    Doubleclick.BidResponse.Ad.Builder ad2 = mapper.buildResponseAd(request, response, bid2);
    assertEquals(bid2.getW(), ad2.getWidth());
    assertEquals(bid2.getH(), ad2.getHeight());
  }

  @Test
  public void testRequest() {
    for (int test = 0; test < 6 * 0b1000; ++test) {
      // -1=NO_SLOT, 0=no size, 1, 2=multisize/1 MAD, 3=2 MAD/1 deal, 4=3 MAD/2 deals
      int size = (test % 6) - 1;
      int flags = test / 6;
      boolean mobile = (flags & 0b1) != 0;
      boolean video = (flags & 0b1) != 0;
      boolean coppa = (flags & 0b1) != 0;
      boolean multiBid = (flags & 0b10) != 0;
      boolean linkExt = (flags & 0b100) != 0;
      String testDesc = String.format("mobile=%s, video=%s, coppa=%s, link=%s, multi=%s/%s",
          mobile, video, coppa, linkExt, size, multiBid);
      ImmutableList.Builder<ExtMapper> extMappers = ImmutableList.builder();
      if (linkExt) {
        extMappers.add(DoubleClickLinkMapper.INSTANCE);
      }
      DoubleClickOpenRtbMapper mapper = new DoubleClickOpenRtbMapper(
          new MetricRegistry(),
          TestUtil.getMetadata(),
          extMappers.build());

      Doubleclick.BidRequest.Builder dcRequest = TestData.newRequest(size, coppa);
      if (mobile) {
        dcRequest.setMobile(TestData.newMobile());
      }
      if (video) {
        dcRequest.setVideo(TestData.newVideo(size));
      }
      BidRequest request = mapper.toOpenRtb(dcRequest.build());

      assertEquals(testDesc, coppa, request.getRegs().hasCoppa());
      assertEquals(testDesc, coppa, request.getRegs().getCoppa() == Flag.YES);

      if (size == TestData.NO_SLOT) {
        assertEquals(0, request.getImpCount());
        BidResponse response = TestUtil.newBidResponse();
        Doubleclick.BidResponse dcResponse = mapper.toNative(request, response).build();
        assertEquals(0, dcResponse.getAdCount());
      } else {
        Impression imp = request.getImp(0);
        assertEquals(testDesc, mobile, request.getDevice().hasOs());
        assertEquals(testDesc, video, imp.hasVideo());
        assertNotEquals(testDesc, video, imp.hasBanner());
        if (video) {
          Banner compAd = imp.getVideo().getCompanionad(0);
          assertEquals(testDesc, size > 1,
              compAd.hasWmin() && compAd.hasWmax() && compAd.hasHmin() && compAd.hasHmax());
          assertNotEquals(testDesc, size != 1, compAd.hasW() && compAd.hasH());
        } else {
          Banner banner = imp.getBanner();
          assertEquals(testDesc, size > 1,
              banner.hasWmin() && banner.hasWmax() && banner.hasHmin() && banner.hasHmax());
          assertNotEquals(testDesc, size != 1, banner.hasW() && banner.hasH());
        }

        Bid.Builder bid = TestData.newBid(multiBid);
        BidResponse response = TestUtil.newBidResponse(bid);
        Doubleclick.BidResponse dcResponse = mapper.toNative(request, response).build();
        if (linkExt) {
          Ad ad = dcResponse.getAd(0);
          assertEquals(testDesc, size > 1 && multiBid, ad.hasWidth());
          assertEquals(testDesc, size > 2 && multiBid, ad.getAdslot(0).hasAdgroupId());
        }
      }
    }
  }

  @Test
  public void testRequest_ping() {
    Doubleclick.BidRequest dcRequest = Doubleclick.BidRequest.newBuilder()
        .setId(TestUtil.REQUEST_ID)
        .setIsPing(true)
        .build();
    OpenRtb.BidRequest request = mapper.toOpenRtb(dcRequest);
    assertEquals(
        OpenRtb.BidRequest.newBuilder().setId(MapperUtil.toHexString(dcRequest.getId())).build(),
        request);
  }

  @Test
  public void testRequest_deals() {
    Doubleclick.BidRequest dcRequest = TestData.newRequest(4, false).build();
    OpenRtb.BidRequest request = mapper.toOpenRtb(dcRequest);
    PMP pmp = request.getImp(0).getPmp();
    assertEquals(2, pmp.getDealsCount());
    PMP.DirectDeal deal = pmp.getDeals(1);
    assertEquals("33", deal.getId());
    assertEquals(12000f, deal.getBidfloor(), 1e-6);
  }

  @Test
  public void testReqResp_NullMapper() {
    NullDoubleClickOpenRtbMapper mapper = NullDoubleClickOpenRtbMapper.INSTANCE;
    assertNotNull(mapper.toNative(
        OpenRtb.BidRequest.getDefaultInstance(), OpenRtb.BidResponse.getDefaultInstance()));
    assertNull(mapper.toOpenRtb(TestData.newRequest()));
  }

  @Test
  public void testExtMapper() {
    ExtMapper extMapper = new ExtMapper() {};
    extMapper.toOpenRtb(
        Doubleclick.BidRequest.getDefaultInstance(),
        OpenRtb.BidRequest.newBuilder());
    extMapper.toOpenRtb(
        Doubleclick.BidRequest.getDefaultInstance(),
        OpenRtb.BidRequest.Device.newBuilder());
    extMapper.toOpenRtb(
        Doubleclick.BidRequest.AdSlot.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.newBuilder());
    extMapper.toOpenRtb(
        Doubleclick.BidRequest.AdSlot.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.Banner.newBuilder());
    extMapper.toOpenRtb(
        Doubleclick.BidRequest.Video.getDefaultInstance(),
        OpenRtb.BidRequest.Impression.Video.newBuilder());
    extMapper.toNative(
        OpenRtb.BidRequest.getDefaultInstance(),
        OpenRtb.BidResponse.getDefaultInstance(),
        OpenRtb.BidResponse.SeatBid.Bid.getDefaultInstance(),
        Doubleclick.BidResponse.Ad.newBuilder());
  }
}